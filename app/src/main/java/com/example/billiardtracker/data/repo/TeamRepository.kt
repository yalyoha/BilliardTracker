package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.AddTeamMemberBody
import com.example.billiardtracker.data.remote.dto.CreateTeamBody
import com.example.billiardtracker.data.remote.dto.RenameTeamBody
import com.example.billiardtracker.data.remote.dto.TeamDto
import com.example.billiardtracker.data.remote.dto.TeamMemberDto

/**
 * Пресеты команд, привязанные к master_token. Общие для владельца и
 * подписчиков — все видят один и тот же список.
 */
class TeamRepository(private val api: ApiService) {

    suspend fun list(tokenId: Long): Result<List<TeamDto>> = try {
        val r = api.listTeams(tokenId)
        if (r.isSuccessful) Result.success(r.body()!!.teams)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun create(tokenId: Long, name: String): Result<TeamDto> = try {
        val r = api.createTeam(tokenId, CreateTeamBody(name))
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun rename(tokenId: Long, teamId: Long, name: String): Result<TeamDto> = try {
        val r = api.renameTeam(tokenId, teamId, RenameTeamBody(name))
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun delete(tokenId: Long, teamId: Long): Result<Unit> = try {
        val r = api.deleteTeam(tokenId, teamId)
        if (r.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun addMember(tokenId: Long, teamId: Long, displayName: String, phone: String?): Result<TeamMemberDto> = try {
        val r = api.addTeamMember(tokenId, teamId, AddTeamMemberBody(displayName, phone))
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun removeMember(tokenId: Long, teamId: Long, memberId: Long): Result<Unit> = try {
        val r = api.deleteTeamMember(tokenId, teamId, memberId)
        if (r.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }
}
