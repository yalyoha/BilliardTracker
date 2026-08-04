package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.LocalIdGenerator
import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.TeamDao
import com.example.billiardtracker.data.local.dao.TeamMemberDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.TeamEntity
import com.example.billiardtracker.data.local.entity.TeamMemberEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.AddTeamMemberBody
import com.example.billiardtracker.data.remote.dto.CreateTeamBody
import com.example.billiardtracker.data.remote.dto.RenameTeamBody
import com.example.billiardtracker.data.remote.dto.TeamDto
import com.example.billiardtracker.data.remote.dto.TeamMemberDto
import com.example.billiardtracker.data.sync.SyncManager
import kotlinx.serialization.json.Json

/**
 * Offline-first team CRUD. Локальные Room-таблицы `teams` + `team_members`
 * — источник истины для UI. Все мутации сначала пишут в Room + enqueue op
 * в outbox, потом sync-worker отправляет их на сервер.
 */
class TeamRepository(
    private val api: ApiService,
    private val teamDao: TeamDao? = null,
    private val memberDao: TeamMemberDao? = null,
    private val outboxDao: OutboxDao? = null,
    private val syncManager: SyncManager? = null,
) {
    private val json = Json { encodeDefaults = true }

    /**
     * Fetch с сервера + upsert в Room. При offline — падает молча, Room
     * (уже содержит локальные + ранее подтянутые) остаётся источником.
     */
    suspend fun refreshFromServer(tokenId: Long): Result<Unit> = try {
        val r = api.listTeams(tokenId)
        if (r.isSuccessful) {
            val server = r.body()!!.teams
            val tDao = teamDao
            val mDao = memberDao
            if (tDao != null && mDao != null) {
                // Заменяем synced records; локальные (id < 0) не трогаем.
                tDao.deleteAllSyncedForToken(tokenId)
                val now = System.currentTimeMillis()
                tDao.upsertAll(server.map { t ->
                    TeamEntity(
                        id = t.id,
                        masterTokenId = t.masterTokenId,
                        name = t.name,
                        createdAt = t.createdAt,
                        serverId = t.id,
                    )
                })
                server.forEach { t ->
                    mDao.deleteByTeam(t.id)
                    mDao.upsertAll(t.members.map { m ->
                        TeamMemberEntity(
                            id = m.id,
                            teamId = m.teamId,
                            displayName = m.displayName,
                            phone = m.phone,
                            addedAt = m.addedAt,
                            serverId = m.id,
                        )
                    })
                }
            }
            Result.success(Unit)
        } else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun list(tokenId: Long): Result<List<TeamDto>> {
        // Пробуем сервер; при ошибке — читаем из Room.
        return try {
            val r = api.listTeams(tokenId)
            if (r.isSuccessful) {
                refreshFromServer(tokenId)
                Result.success(r.body()!!.teams)
            } else fromRoom(tokenId)
        } catch (e: Exception) { fromRoom(tokenId) }
    }

    private suspend fun fromRoom(tokenId: Long): Result<List<TeamDto>> {
        val tDao = teamDao ?: return Result.success(emptyList())
        val mDao = memberDao ?: return Result.success(emptyList())
        val teams = tDao.listByToken(tokenId)
        val out = teams.map { t ->
            val members = mDao.listByTeam(t.id).map {
                TeamMemberDto(
                    id = it.id,
                    teamId = it.teamId,
                    displayName = it.displayName,
                    phone = it.phone,
                    addedAt = it.addedAt,
                )
            }
            TeamDto(
                id = t.id,
                masterTokenId = t.masterTokenId,
                name = t.name,
                createdAt = t.createdAt,
                members = members,
            )
        }
        return Result.success(out)
    }

    suspend fun create(tokenId: Long, name: String): Result<TeamDto> {
        val tDao = teamDao
        val outbox = outboxDao
        if (tDao == null || outbox == null) {
            return try {
                val r = api.createTeam(tokenId, CreateTeamBody(name))
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val localId = LocalIdGenerator.next()
        val now = System.currentTimeMillis()
        val local = TeamEntity(
            id = localId,
            masterTokenId = tokenId,
            name = name,
            createdAt = now,
            serverId = null,
        )
        tDao.upsert(local)
        outbox.insert(
            OutboxOpEntity(
                kind = "create_team",
                payloadJson = json.encodeToString(CreateTeamBody.serializer(), CreateTeamBody(name)),
                endpoint = "api/tokens/$tokenId/teams",
                method = "POST",
                masterTokenId = tokenId,
                localTeamId = localId,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        return Result.success(
            TeamDto(id = localId, masterTokenId = tokenId, name = name, createdAt = now, members = emptyList())
        )
    }

    suspend fun rename(tokenId: Long, teamId: Long, name: String): Result<TeamDto> {
        val tDao = teamDao
        val outbox = outboxDao
        if (tDao == null || outbox == null) {
            return try {
                val r = api.renameTeam(tokenId, teamId, RenameTeamBody(name))
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val local = tDao.getById(teamId)
        if (local != null) tDao.upsert(local.copy(name = name))
        val now = System.currentTimeMillis()
        outbox.insert(
            OutboxOpEntity(
                kind = "rename_team",
                payloadJson = json.encodeToString(RenameTeamBody.serializer(), RenameTeamBody(name)),
                endpoint = "api/tokens/$tokenId/teams/$teamId",
                method = "PATCH",
                masterTokenId = tokenId,
                localTeamId = teamId,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        return Result.success(
            TeamDto(
                id = teamId,
                masterTokenId = tokenId,
                name = name,
                createdAt = local?.createdAt ?: now,
                members = emptyList(),
            )
        )
    }

    suspend fun delete(tokenId: Long, teamId: Long): Result<Unit> {
        val tDao = teamDao
        val mDao = memberDao
        val outbox = outboxDao
        if (tDao == null || mDao == null || outbox == null) {
            return try {
                val r = api.deleteTeam(tokenId, teamId)
                if (r.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        // Локально: удалить team + всех её members.
        tDao.deleteById(teamId)
        mDao.deleteByTeam(teamId)
        if (teamId < 0) {
            // Локальный, не синкнутый — отменяем pending create_team +
            // add_team_member для этой команды.
            outbox.pendingOps()
                .filter { it.localTeamId == teamId }
                .forEach { outbox.update(it.copy(executed = true, lastError = "cancelled by team delete")) }
        } else {
            outbox.insert(
                OutboxOpEntity(
                    kind = "delete_team",
                    payloadJson = "",
                    endpoint = "api/tokens/$tokenId/teams/$teamId",
                    method = "DELETE",
                    masterTokenId = tokenId,
                    localTeamId = teamId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            syncManager?.kickDrain()
        }
        return Result.success(Unit)
    }

    suspend fun addMember(tokenId: Long, teamId: Long, displayName: String, phone: String?): Result<TeamMemberDto> {
        val mDao = memberDao
        val outbox = outboxDao
        if (mDao == null || outbox == null) {
            return try {
                val r = api.addTeamMember(tokenId, teamId, AddTeamMemberBody(displayName, phone))
                if (r.isSuccessful) Result.success(r.body()!!)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        val localId = LocalIdGenerator.next()
        val now = System.currentTimeMillis()
        val local = TeamMemberEntity(
            id = localId,
            teamId = teamId,
            displayName = displayName,
            phone = phone,
            addedAt = now,
            serverId = null,
        )
        mDao.upsert(local)
        outbox.insert(
            OutboxOpEntity(
                kind = "add_team_member",
                payloadJson = json.encodeToString(
                    AddTeamMemberBody.serializer(),
                    AddTeamMemberBody(displayName, phone),
                ),
                endpoint = "api/tokens/$tokenId/teams/$teamId/members",
                method = "POST",
                masterTokenId = tokenId,
                localTeamId = teamId,
                localMemberId = localId,
                createdAt = now,
            )
        )
        syncManager?.kickDrain()
        return Result.success(
            TeamMemberDto(
                id = localId,
                teamId = teamId,
                displayName = displayName,
                phone = phone,
                addedAt = now,
            )
        )
    }

    suspend fun removeMember(tokenId: Long, teamId: Long, memberId: Long): Result<Unit> {
        val mDao = memberDao
        val outbox = outboxDao
        if (mDao == null || outbox == null) {
            return try {
                val r = api.deleteTeamMember(tokenId, teamId, memberId)
                if (r.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${r.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
        mDao.deleteById(memberId)
        if (memberId < 0) {
            outbox.pendingOps()
                .filter { it.localMemberId == memberId && it.kind == "add_team_member" }
                .forEach { outbox.update(it.copy(executed = true, lastError = "cancelled by member delete")) }
        } else {
            outbox.insert(
                OutboxOpEntity(
                    kind = "delete_team_member",
                    payloadJson = "",
                    endpoint = "api/tokens/$tokenId/teams/$teamId/members/$memberId",
                    method = "DELETE",
                    masterTokenId = tokenId,
                    localTeamId = teamId,
                    localMemberId = memberId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            syncManager?.kickDrain()
        }
        return Result.success(Unit)
    }
}
