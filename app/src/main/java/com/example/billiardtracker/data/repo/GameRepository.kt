package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.dao.GameDao
import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ShotDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.ShotEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.ClaimRefereeResponse
import com.example.billiardtracker.data.remote.dto.CreateShotBody
import com.example.billiardtracker.data.remote.dto.FinishGameBody
import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.ShotDto
import com.example.billiardtracker.data.sync.SyncManager
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

class GameRepository(
    private val api: ApiService,
    private val shotDao: ShotDao? = null,
    private val outboxDao: OutboxDao? = null,
    private val syncManager: SyncManager? = null,
    private val gameDao: GameDao? = null,
) {
    // Локальный ID-генератор: отрицательные ID стартуют от -currentTimeMillis()
    // и уменьшаются. Так они гарантированно не столкнутся с серверными
    // (positive autoincrement) даже между сессиями.
    private val localIdGen = AtomicLong(-System.currentTimeMillis())
    private fun nextLocalId(): Long = localIdGen.decrementAndGet()

    private val json = Json { encodeDefaults = true }

    suspend fun listGames(tid: Long): Result<List<GameDto>> = try {
        val r = api.listGames(tid)
        if (r.isSuccessful) Result.success(r.body()!!.games)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun startGame(tid: Long): Result<GameDto> = try {
        val r = api.startGame(tid)
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Offline-first finish_game. Локально обновляем GameEntity + enqueue
     * op. Если сеть есть — SyncManager сразу отправит. Если нет —
     * отправит когда появится. UI получает мгновенный успех.
     */
    suspend fun finishGame(tid: Long, gid: Long, winnerPid: Long?): Result<GameDto> {
        val outbox = outboxDao
        val games = gameDao
        // Fallback (тесты): без Room — online-only.
        if (outbox == null || games == null) {
            return try {
                val r = api.finishGame(tid, gid, FinishGameBody(winnerPid))
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val now = System.currentTimeMillis()
        val body = FinishGameBody(winnerPid)
        // Оптимистично обновляем локальный Game (если он в Room).
        val local = games.getById(gid)
        if (local != null) {
            games.upsert(
                local.copy(
                    status = "finished",
                    winnerParticipantId = winnerPid,
                    finishedAt = now,
                )
            )
        }
        outbox.insert(
            OutboxOpEntity(
                kind = "finish_game",
                payloadJson = json.encodeToString(FinishGameBody.serializer(), body),
                endpoint = "api/tournaments/$tid/games/$gid/finish",
                method = "POST",
                localTournamentId = tid,
                localGameId = gid,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        // Возвращаем оптимистичный DTO (без scores — VM их сам держит).
        return Result.success(
            GameDto(
                id = gid,
                tournamentId = tid,
                orderIndex = local?.orderIndex ?: 0,
                status = "finished",
                startedAt = local?.startedAt ?: now,
                finishedAt = now,
                winnerParticipantId = winnerPid,
            )
        )
    }

    /**
     * Offline-first: пишем shot в Room с локальным ID сразу, ставим op в
     * outbox, кикаем sync. UI видит удар мгновенно, серверный ID
     * подставится когда придёт ответ (см. SyncManager.onSuccess).
     */
    suspend fun addShot(
        gid: Long,
        participantId: Long,
        kind: String,
        ballNumber: Int?,
        pointsDelta: Int,
    ): Result<ShotDto> {
        val body = CreateShotBody(participantId, kind, ballNumber, pointsDelta)
        // Если Room/outbox не подключены (тесты) — падаем на online.
        val dao = shotDao
        val outbox = outboxDao
        if (dao == null || outbox == null) {
            return try {
                val r = api.addShot(gid, body)
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }

        val localId = nextLocalId()
        val local = ShotEntity(
            id = localId,
            gameId = gid,
            participantId = participantId,
            kind = kind,
            ballNumber = ballNumber,
            pointsDelta = pointsDelta,
            ts = System.currentTimeMillis(),
            enteredByUserId = 0,
            lastSyncedAt = 0L,
        )
        dao.upsert(local)
        outbox.insert(
            OutboxOpEntity(
                kind = "add_shot",
                payloadJson = json.encodeToString(CreateShotBody.serializer(), body),
                endpoint = "api/games/$gid/shots",
                method = "POST",
                localGameId = gid,
                localShotId = localId,
                createdAt = System.currentTimeMillis(),
            )
        )
        syncManager?.kickDrain()
        return Result.success(
            ShotDto(
                id = localId,
                gameId = gid,
                participantId = participantId,
                kind = kind,
                ballNumber = ballNumber,
                pointsDelta = pointsDelta,
                ts = local.ts,
                enteredByUserId = 0,
            )
        )
    }

    /**
     * Offline-first delete. Если shot всё ещё локальный (не синкнутый),
     * то дополнительно нужно отменить его pending add_shot — иначе
     * сервер получит POST после нашего DELETE.
     */
    suspend fun deleteShot(gid: Long, sid: Long): Result<Unit> {
        val dao = shotDao
        val outbox = outboxDao
        if (dao == null || outbox == null) {
            return try {
                val r = api.deleteShot(gid, sid)
                if (r.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }

        dao.deleteById(sid)
        if (sid < 0) {
            // Локальный shot — отменяем pending add_shot чтобы не отправлялся.
            outbox.pendingOps().filter { it.kind == "add_shot" && it.localShotId == sid }
                .forEach { outbox.update(it.copy(executed = true, lastError = "cancelled by delete")) }
        } else {
            // Серверный shot — enqueue DELETE.
            outbox.insert(
                OutboxOpEntity(
                    kind = "delete_shot",
                    payloadJson = "",
                    endpoint = "api/games/$gid/shots/$sid",
                    method = "DELETE",
                    localGameId = gid,
                    localShotId = sid,
                    createdAt = System.currentTimeMillis(),
                )
            )
            syncManager?.kickDrain()
        }
        return Result.success(Unit)
    }

    suspend fun listShots(gid: Long): Result<List<ShotDto>> = try {
        val r = api.listShots(gid)
        if (r.isSuccessful) Result.success(r.body()!!.shots)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun claimReferee(tid: Long): Result<ClaimRefereeResponse> = try {
        val r = api.claimReferee(tid)
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun transferReferee(tid: Long, toUserId: Long): Result<Unit> = try {
        val r = api.transferReferee(tid, mapOf("toUserId" to toUserId))
        if (r.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
