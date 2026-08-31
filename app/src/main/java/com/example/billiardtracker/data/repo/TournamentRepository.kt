package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.LocalIdGenerator
import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.dao.TournamentDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.remote.dto.ParticipantDto
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class TournamentRepository(
    private val api: ApiService,
    private val tournamentDao: TournamentDao,
    private val participantDao: ParticipantDao,
    private val outboxDao: OutboxDao? = null,
    private val syncManager: SyncManager? = null,
) {
    private val json = Json { encodeDefaults = true }

    fun observeAll(): Flow<List<TournamentEntity>> = tournamentDao.observeAll()

    suspend fun refreshMine(tokenId: Long? = null): Result<Unit> = try {
        val res = api.getMyTournaments(tokenId)
        if (!res.isSuccessful) {
            Result.failure(IllegalStateException("HTTP ${res.code()}"))
        } else {
            val now = System.currentTimeMillis()
            val entities = res.body()!!.tournaments.map {
                TournamentEntity(
                    id = it.id,
                    title = it.title,
                    clubId = null,
                    gameType = it.gameType,
                    moneyPerBallKop = null,
                    createdByUserId = 0,
                    refereeUserId = null,
                    status = it.status,
                    startedAt = it.startedAt,
                    finishedAt = null,
                    lastSyncedAt = now,
                    serverId = it.id,
                )
            }
            // Запоминаем ID встреч, скрытых пользователем, до удаления таблицы.
            val hiddenIds = tournamentDao.hiddenServerIds().toSet()
            tournamentDao.deleteAll()
            tournamentDao.upsertAll(entities)
            // Повторно удаляем скрытые — они пришли с сервера, но пользователь убрал их вручную.
            hiddenIds.forEach { tournamentDao.deleteById(it) }
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Offline-fallback: если API упал (нет сети / 404 для локального id) —
     * возвращаем DTO из Room. Позволяет открыть турнир offline.
     */
    suspend fun fetchDetail(id: Long): Result<TournamentDto> {
        // Если local id уже смапился на server (create_tournament синкнулся) —
        // сразу берём server id, иначе fromRoom вернёт not_found (сущность
        // была delete+re-inserted с server id).
        val resolved = if (id < 0) syncManager?.resolveTournamentId(id) ?: id else id
        // Для локального id (< 0) API запрос бессмыслен — сразу Room.
        if (resolved < 0) return fromRoom(resolved)
        return try {
            val res = api.getTournament(resolved)
            if (res.isSuccessful) {
                val dto = res.body()!!
                cacheDetail(dto)
                Result.success(dto)
            } else {
                fromRoom(resolved)
            }
        } catch (e: Exception) {
            fromRoom(resolved)
        }
    }

    private suspend fun fromRoom(id: Long): Result<TournamentDto> {
        val local = tournamentDao.getById(id)
            ?: return Result.failure(IllegalStateException("not_found_local"))
        val parts = participantDao.listByTournament(id).map { it.toDto() }
        return Result.success(local.toDto(parts))
    }

    private suspend fun cacheDetail(dto: TournamentDto) {
        val now = System.currentTimeMillis()
        tournamentDao.upsert(
            TournamentEntity(
                id = dto.id,
                title = dto.title,
                clubId = dto.clubId,
                gameType = dto.gameType,
                moneyPerBallKop = dto.moneyPerBallKop,
                createdByUserId = dto.createdByUserId,
                refereeUserId = dto.refereeUserId,
                status = dto.status,
                startedAt = dto.startedAt,
                finishedAt = dto.finishedAt,
                lastSyncedAt = now,
                serverId = dto.id,
            ),
        )
        participantDao.upsertAll(
            dto.participants.map {
                ParticipantEntity(
                    id = it.id,
                    tournamentId = dto.id,
                    userId = it.userId,
                    displayName = it.displayName,
                    handicapPoints = it.handicapPoints,
                    perBallOverrideKop = it.perBallOverrideKop,
                    lastSyncedAt = now,
                )
            },
        )
    }

    /** Offline-first finish tournament. */
    suspend fun finish(id: Long): Result<TournamentDto> {
        val outbox = outboxDao
        if (outbox == null) {
            return try {
                val res = api.finishTournament(id)
                if (res.isSuccessful) {
                    val dto = res.body()!!
                    fetchDetail(id)
                    Result.success(dto)
                } else Result.failure(IllegalStateException("HTTP ${res.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val now = System.currentTimeMillis()
        val local = tournamentDao.getById(id)
        if (local != null) {
            tournamentDao.upsert(local.copy(status = "finished", finishedAt = now))
        }
        outbox.insert(
            OutboxOpEntity(
                kind = "finish_tournament",
                payloadJson = "",
                endpoint = "api/tournaments/$id/finish",
                method = "POST",
                localTournamentId = id,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        val existing = local
        return Result.success(
            TournamentDto(
                id = id,
                title = existing?.title,
                gameType = existing?.gameType ?: "",
                createdByUserId = existing?.createdByUserId ?: 0,
                status = "finished",
                startedAt = existing?.startedAt ?: now,
                finishedAt = now,
                refereeUserId = existing?.refereeUserId,
                clubId = existing?.clubId,
                moneyPerBallKop = existing?.moneyPerBallKop,
            )
        )
    }

    /**
     * Скрыть встречу из «Идут сейчас». Помечаем статус local_hidden — при следующем
     * refreshMine() встреча придёт с сервера, но будет удалена снова (см. refreshMine).
     */
    suspend fun deleteLocal(id: Long) {
        val entity = tournamentDao.getById(id)
        if (entity != null) {
            tournamentDao.upsert(entity.copy(status = "local_hidden"))
        } else {
            tournamentDao.deleteById(id)
        }
    }

    /**
     * Offline-first create. Локально: TournamentEntity + Participants с
     * negative IDs. Enqueue op. При sync — SyncManager делает cascade
     * remap для games/shots/outbox по мере поступления серверных IDs.
     *
     * currentUserId — id залогиненого юзера, используется для локального
     * зеркала серверного поведения (`created_by_user_id` = `referee_user_id`
     * = userId, см. backend/routes/tournaments.js). Без этого `isReferee`
     * в TournamentViewModel был бы false до первой удачной синхронизации.
     */
    suspend fun create(body: CreateTournamentBody, currentUserId: Long): Result<TournamentDto> {
        val outbox = outboxDao
        if (outbox == null) {
            return try {
                val res = api.createTournament(body)
                if (!res.isSuccessful) Result.failure(IllegalStateException("HTTP ${res.code()}"))
                else {
                    val dto = res.body()!!
                    cacheDetail(dto)
                    Result.success(dto)
                }
            } catch (e: Exception) { Result.failure(e) }
        }
        val now = System.currentTimeMillis()
        val localTid = LocalIdGenerator.next()
        val local = TournamentEntity(
            id = localTid,
            title = body.title,
            clubId = body.clubId,
            gameType = body.gameType,
            moneyPerBallKop = body.moneyPerBallKop,
            createdByUserId = currentUserId,
            refereeUserId = currentUserId,
            status = "active",
            startedAt = now,
            finishedAt = null,
            lastSyncedAt = 0L,
            serverId = null,
        )
        tournamentDao.upsert(local)
        val localParticipants = body.participants.map { p ->
            ParticipantEntity(
                id = LocalIdGenerator.next(),
                tournamentId = localTid,
                userId = null,
                displayName = p.displayName,
                handicapPoints = p.handicapPoints,
                perBallOverrideKop = p.perBallOverrideKop,
                lastSyncedAt = 0L,
            )
        }
        participantDao.upsertAll(localParticipants)
        outbox.insert(
            OutboxOpEntity(
                kind = "create_tournament",
                payloadJson = json.encodeToString(CreateTournamentBody.serializer(), body),
                endpoint = "api/tournaments",
                method = "POST",
                localTournamentId = localTid,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        return Result.success(local.toDto(localParticipants.map { it.toDto() }))
    }

    private fun TournamentEntity.toDto(parts: List<ParticipantDto>): TournamentDto = TournamentDto(
        id = id,
        title = title,
        clubId = clubId,
        gameType = gameType,
        moneyPerBallKop = moneyPerBallKop,
        createdByUserId = createdByUserId,
        refereeUserId = refereeUserId,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        participants = parts,
    )

    private fun ParticipantEntity.toDto(): ParticipantDto = ParticipantDto(
        id = id,
        userId = userId,
        displayName = displayName,
        handicapPoints = handicapPoints,
        perBallOverrideKop = perBallOverrideKop,
    )
}
