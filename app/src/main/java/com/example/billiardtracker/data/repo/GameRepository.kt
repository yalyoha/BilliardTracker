package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.ClaimRefereeResponse
import com.example.billiardtracker.data.remote.dto.CreateShotBody
import com.example.billiardtracker.data.remote.dto.FinishGameBody
import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.ShotDto

class GameRepository(private val api: ApiService) {
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

    suspend fun finishGame(tid: Long, gid: Long, winnerPid: Long?): Result<GameDto> = try {
        val r = api.finishGame(tid, gid, FinishGameBody(winnerPid))
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addShot(
        gid: Long,
        participantId: Long,
        kind: String,
        ballNumber: Int?,
        pointsDelta: Int,
    ): Result<ShotDto> = try {
        val r = api.addShot(gid, CreateShotBody(participantId, kind, ballNumber, pointsDelta))
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteShot(gid: Long, sid: Long): Result<Unit> = try {
        val r = api.deleteShot(gid, sid)
        if (r.isSuccessful) Result.success(Unit)
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
}
