package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.dao.TournamentDao
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.remote.dto.TournamentDto
import kotlinx.coroutines.flow.Flow

class TournamentRepository(
    private val api: ApiService,
    private val tournamentDao: TournamentDao,
    private val participantDao: ParticipantDao,
) {
    fun observeAll(): Flow<List<TournamentEntity>> = tournamentDao.observeAll()

    suspend fun refreshMine(): Result<Unit> = try {
        val res = api.getMyTournaments()
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
