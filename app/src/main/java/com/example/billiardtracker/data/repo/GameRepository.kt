package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.dao.GameDao
import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ShotDao
import com.example.billiardtracker.data.local.entity.GameEntity
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.ShotEntity
import kotlinx.coroutines.flow.first
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

    /**
     * Online — API + upsert в Room. Offline — читаем из Room. Всегда
     * возвращаем что есть, даже без сети.
     */
    suspend fun listGames(tid: Long): Result<List<GameDto>> {
        val games = gameDao
        // tid из VM — локальный negative до первого refresh, но после
        // create_tournament-remap все Room-игры уже под серверным tid. Без
        // резолва listGames уходит в оффлайн-ветку и readGamesFromRoom(NEG)
        // возвращает пусто → refresh() не видит игр → currentGame залипает
        // на устаревшей local negative версии.
        val resolvedTid = syncManager?.resolveTournamentId(tid) ?: tid
        if (resolvedTid < 0) {
            // Локальный турнир — сервер о нём не знает, только Room.
            return Result.success(readGamesFromRoom(resolvedTid))
        }
        return try {
            val r = api.listGames(resolvedTid)
            if (r.isSuccessful) {
                val list = r.body()!!.games
                // Upsert в Room чтобы offline потом мог прочитать.
                games?.let { dao ->
                    val now = System.currentTimeMillis()
                    dao.upsertAll(list.map { g ->
                        com.example.billiardtracker.data.local.entity.GameEntity(
                            id = g.id,
                            tournamentId = g.tournamentId,
                            orderIndex = g.orderIndex,
                            status = g.status,
                            startedAt = g.startedAt,
                            finishedAt = g.finishedAt,
                            winnerParticipantId = g.winnerParticipantId,
                            lastSyncedAt = now,
                            serverId = g.id,
                        )
                    })
                }
                Result.success(list)
            } else Result.success(readGamesFromRoom(resolvedTid))
        } catch (e: Exception) {
            Result.success(readGamesFromRoom(resolvedTid))
        }
    }

    private suspend fun readGamesFromRoom(tid: Long): List<GameDto> {
        val games = gameDao ?: return emptyList()
        return games.observeByTournament(tid).first().map { e ->
            GameDto(
                id = e.id,
                tournamentId = e.tournamentId,
                orderIndex = e.orderIndex,
                status = e.status,
                startedAt = e.startedAt,
                finishedAt = e.finishedAt,
                winnerParticipantId = e.winnerParticipantId,
                scores = emptyList(),
            )
        }
    }

    /**
     * Offline-first start_game. Локально создаём GameEntity с отрицательным
     * ID + enqueue POST в outbox. SyncManager при появлении сети создаст
     * игру на сервере и подставит серверный ID в GameEntity.serverId.
     * Downstream ops (add_shot, finish_game) резолвят serverId в
     * SyncManager.resolveEndpoint при отправке.
     */
    suspend fun startGame(tid: Long): Result<GameDto> {
        val outbox = outboxDao
        val games = gameDao
        if (outbox == null || games == null) {
            return try {
                val r = api.startGame(tid)
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val localId = nextLocalId()
        val now = System.currentTimeMillis()
        val existing = games.observeByTournament(tid).first()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: 0) + 1
        val local = GameEntity(
            id = localId,
            tournamentId = tid,
            orderIndex = nextOrder,
            status = "active",
            startedAt = now,
            finishedAt = null,
            winnerParticipantId = null,
            lastSyncedAt = 0L,
            serverId = null,
        )
        games.upsert(local)
        outbox.insert(
            OutboxOpEntity(
                kind = "start_game",
                payloadJson = "{}",
                endpoint = "api/tournaments/$tid/games",
                method = "POST",
                localTournamentId = tid,
                localGameId = localId,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        return Result.success(
            GameDto(
                id = localId,
                tournamentId = tid,
                orderIndex = nextOrder,
                status = "active",
                startedAt = now,
            )
        )
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
        // gid из VM — локальный negative до первого refresh с сервера, но Room
        // после start_game-remap хранит игру уже под серверным id. Без резолва
        // getById(gid) промахивается → local upsert("finished") пропускается →
        // refresh() возвращает active → RefereePanel не скрывается («Партия
        // окончена» кажется сломанной).
        val resolvedGid = syncManager?.resolveGameId(gid) ?: gid
        // Оптимистично обновляем локальный Game (если он в Room).
        val local = games.getById(resolvedGid)
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
                id = resolvedGid,
                tournamentId = syncManager?.resolveTournamentId(tid) ?: tid,
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
        // ViewModel держит participantId из fromRoom() до refresh() с сервера;
        // после успешного create_tournament participant remapнулся, но VM ещё
        // знает старый local pid. Резолвим тут, чтобы серверу ушёл настоящий id.
        val resolvedPid = syncManager?.resolveParticipantId(participantId) ?: participantId
        val body = CreateShotBody(resolvedPid, kind, ballNumber, pointsDelta)
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
            participantId = resolvedPid,
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
                participantId = resolvedPid,
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

    suspend fun listShots(gid: Long): Result<List<ShotDto>> {
        val shots = shotDao
        if (gid < 0) {
            return Result.success(readShotsFromRoom(gid))
        }
        return try {
            val r = api.listShots(gid)
            if (r.isSuccessful) {
                val list = r.body()!!.shots
                shots?.upsertAll(list.map { s ->
                    ShotEntity(
                        id = s.id,
                        gameId = s.gameId,
                        participantId = s.participantId,
                        kind = s.kind,
                        ballNumber = s.ballNumber,
                        pointsDelta = s.pointsDelta,
                        ts = s.ts,
                        enteredByUserId = s.enteredByUserId,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                })
                // Локальные pending shots ещё не долетели до сервера, но уже в Room
                // (gameId после start_game-remap уже серверный). Мержим, иначе после
                // remap игры refresh() «съедает» только что нажатый «+» и счёт
                // визуально сбрасывается на 0.
                val localPending = readShotsFromRoom(gid).filter { it.id < 0 }
                Result.success(list + localPending)
            } else Result.success(readShotsFromRoom(gid))
        } catch (e: Exception) {
            Result.success(readShotsFromRoom(gid))
        }
    }

    private suspend fun readShotsFromRoom(gid: Long): List<ShotDto> {
        val shots = shotDao ?: return emptyList()
        return shots.observeByGame(gid).first().map { e ->
            ShotDto(
                id = e.id,
                gameId = e.gameId,
                participantId = e.participantId,
                kind = e.kind,
                ballNumber = e.ballNumber,
                pointsDelta = e.pointsDelta,
                ts = e.ts,
                enteredByUserId = e.enteredByUserId,
            )
        }
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
