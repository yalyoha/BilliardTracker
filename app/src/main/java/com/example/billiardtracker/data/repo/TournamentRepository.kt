package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.dao.TournamentDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class TournamentRepository(
    private val api: ApiService,
    private val tournamentDao: TournamentDao,
    private val participantDao: ParticipantDao,
    private val outboxDao: OutboxDao? = null,
    private val syncManager: SyncManager? = null,
) {
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
                )
            }
            // Full replace: switching the active token would otherwise leave
            // stale tournaments from the previous token visible until they
            // scrolled off the DAO's ordering.
            tournamentDao.deleteAll()
            tournamentDao.upsertAll(entities)
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun fetchDetail(id: Long): Result<TournamentDto> = try {
        val res = api.getTournament(id)
        if (!res.isSuccessful) {
            Result.failure(IllegalStateException("HTTP ${res.code()}"))
        } else {
            val dto = res.body()!!
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
            Result.success(dto)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Offline-first: обновляем локальный TournamentEntity (status='finished'),
     * enqueue op. Sync worker отправит POST /finish когда сеть будет. UI
     * получает мгновенный успех.
     */
    suspend fun finish(id: Long): Result<TournamentDto> {
        val outbox = outboxDao
        // Fallback (тесты без Room): чистый online.
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
        // Оптимистичный DTO — VM использует его чтобы обновить UI. Часть
        // полей (участники) не заполняем — VM держит их из refresh().
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

    suspend fun create(body: CreateTournamentBody): Result<TournamentDto> = try {
        val res = api.createTournament(body)
        if (!res.isSuccessful) {
            Result.failure(IllegalStateException("HTTP ${res.code()}"))
        } else {
            val dto = res.body()!!
            // Ensures locally cached with participants.
            fetchDetail(dto.id)
            Result.success(dto)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
