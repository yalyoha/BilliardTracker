package com.example.billiardtracker.data.sync

import com.example.billiardtracker.data.local.dao.GameDao
import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.dao.ShotDao
import com.example.billiardtracker.data.local.dao.TeamDao
import com.example.billiardtracker.data.local.dao.TeamMemberDao
import com.example.billiardtracker.data.local.dao.TournamentDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.TeamEntity
import com.example.billiardtracker.data.local.entity.TeamMemberEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/**
 * Драйнит outbox: берёт pending операции по одной, шлёт на сервер, помечает
 * executed. Реактивно триггерится когда NetworkMonitor.online → true.
 *
 * Фаза 1: обрабатывает только add_shot / delete_shot. Остальные виды
 * операций будут добавлены в v1.15.0/v1.16.0.
 */
class SyncManager(
    private val outboxDao: OutboxDao,
    private val shotDao: ShotDao,
    private val gameDao: GameDao,
    private val tournamentDao: TournamentDao,
    private val participantDao: ParticipantDao,
    private val teamDao: TeamDao,
    private val teamMemberDao: TeamMemberDao,
    private val networkMonitor: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    private val http = OkHttpClient()
    private val syncMutex = Mutex()

    // Broadcast: local id → server id. Слушатели (TeamState / ViewModels)
    // могут смигрировать своё in-memory состояние без полного refresh.
    private val _teamIdRemaps = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 32)
    val teamIdRemaps: SharedFlow<Pair<Long, Long>> = _teamIdRemaps.asSharedFlow()

    init {
        // Триггер: как только сеть появилась — драйним очередь. Ошибки
        // ловим на месте, чтобы uncaught exception в фоновом коррутине
        // не убил app process.
        appScope.launch {
            try {
                networkMonitor.online.collect { online ->
                    if (online) runCatching { drain() }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Force-drain — можно позвать из репо сразу после enqueue. */
    fun kickDrain() {
        appScope.launch { runCatching { drain() } }
    }

    private suspend fun drain() {
        if (!syncMutex.tryLock()) return
        try {
            val ops = try { outboxDao.pendingOps() } catch (_: Throwable) { return }
            // Проходим все ops. НЕ прерываемся при неудаче: могут быть
            // независимые ops дальше (напр. delete_shot для серверного shot).
            // Deps резолвятся автоматически: parent op завершается первым по
            // FIFO, чильд-op на след. drain'e увидит serverId.
            for (op in ops) {
                try { executeOne(op) } catch (_: Throwable) { }
            }
            runCatching { outboxDao.purgeExecuted() }
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Пересобирает endpoint для op'а, подставляя серверные ID вместо локальных
     * отрицательных. Возвращает null если необходимая dep-entity ещё не
     * синкнута (нет serverId) — SyncManager пропустит и попробует позже.
     */
    private suspend fun resolveEndpoint(op: OutboxOpEntity): String? {
        val tid = op.localTournamentId
        val gid = op.localGameId
        val teamId = op.localTeamId
        val realTid = when {
            tid == null -> null
            tid >= 0 -> tid
            else -> tournamentDao.getById(tid)?.serverId ?: return null
        }
        val realGid = when {
            gid == null -> null
            gid >= 0 -> gid
            else -> gameDao.getById(gid)?.serverId ?: return null
        }
        val realTeamId = when {
            teamId == null -> null
            teamId >= 0 -> teamId
            else -> teamDao.getById(teamId)?.serverId ?: return null
        }
        val tokenId = op.masterTokenId
        return when (op.kind) {
            "create_tournament" -> "api/tournaments"
            "start_game" -> "api/tournaments/$realTid/games"
            "finish_game" -> "api/tournaments/$realTid/games/$realGid/finish"
            "finish_tournament" -> "api/tournaments/$realTid/finish"
            "add_shot" -> "api/games/$realGid/shots"
            "delete_shot" -> {
                val sid = op.localShotId ?: return null
                if (sid < 0) return null
                "api/games/$realGid/shots/$sid"
            }
            "create_team" -> "api/tokens/$tokenId/teams"
            "rename_team" -> "api/tokens/$tokenId/teams/$realTeamId"
            "delete_team" -> "api/tokens/$tokenId/teams/$realTeamId"
            "add_team_member" -> "api/tokens/$tokenId/teams/$realTeamId/members"
            "delete_team_member" -> {
                val mid = op.localMemberId ?: return null
                if (mid < 0) return null
                "api/tokens/$tokenId/teams/$realTeamId/members/$mid"
            }
            else -> op.endpoint
        }
    }

    private suspend fun executeOne(op: OutboxOpEntity): Boolean {
        val jwt = tokenProvider() ?: return false
        // Пересобираем endpoint с учётом того, что local IDs (отрицательные)
        // могли получить серверные serverId — подставляем их. Если зависимость
        // ещё не синкнута — пропускаем op, retry позже.
        val resolvedEndpoint = resolveEndpoint(op)
            ?: return false // dep not ready, retry позже когда родитель синкнется
        return try {
            val url = baseUrl.trimEnd('/') + "/" + resolvedEndpoint.trimStart('/')
            val builder = Request.Builder().url(url).header("Authorization", "Bearer $jwt")
            val body = op.payloadJson.toRequestBody("application/json".toMediaType())
            when (op.method.uppercase()) {
                "POST" -> builder.post(body)
                "DELETE" -> builder.delete()
                "PATCH" -> builder.patch(body)
                "PUT" -> builder.put(body)
                else -> return false
            }
            val res = http.newCall(builder.build()).execute()
            res.use { r ->
                when {
                    r.isSuccessful -> {
                        onSuccess(op, r.body?.string().orEmpty())
                        outboxDao.update(op.copy(executed = true))
                        true
                    }
                    r.code in 400..499 && r.code != 401 && r.code != 408 && r.code != 429 -> {
                        // 4xx (кроме 401/408/429) — необратимая ошибка, дальше пытаться бессмысленно
                        outboxDao.update(op.copy(
                            executed = true,
                            lastError = "HTTP ${r.code}: rejected",
                        ))
                        true
                    }
                    else -> {
                        // 5xx / network — retry позже
                        outboxDao.update(op.copy(
                            attempts = op.attempts + 1,
                            lastError = "HTTP ${r.code}",
                        ))
                        false
                    }
                }
            }
        } catch (e: Exception) {
            outboxDao.update(op.copy(
                attempts = op.attempts + 1,
                lastError = e.message ?: "network error",
            ))
            false
        }
    }

    /**
     * Пост-обработка успешной операции: обновить local entity серверным ID.
     * Пока — только для add_shot (получаем shot.id с сервера и обновляем
     * локальный row).
     */
    private suspend fun onSuccess(op: OutboxOpEntity, responseBody: String) {
        when (op.kind) {
            "finish_game", "finish_tournament", "delete_shot",
            "rename_team", "delete_team", "delete_team_member" -> {}
            "create_team" -> {
                val localTeamId = op.localTeamId ?: return
                if (localTeamId >= 0) return
                val serverTeam = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        com.example.billiardtracker.data.remote.dto.TeamDto.serializer(),
                        responseBody,
                    )
                }.getOrNull() ?: return
                val local = teamDao.getById(localTeamId) ?: return
                teamDao.deleteById(localTeamId)
                teamDao.upsert(
                    TeamEntity(
                        id = serverTeam.id,
                        masterTokenId = serverTeam.masterTokenId,
                        name = serverTeam.name,
                        createdAt = serverTeam.createdAt,
                        serverId = serverTeam.id,
                    )
                )
                // Cascade: team_members.teamId + pending outbox ops.
                teamMemberDao.remapTeamId(localTeamId, serverTeam.id)
                outboxDao.pendingOps()
                    .filter { it.localTeamId == localTeamId }
                    .forEach { outboxDao.update(it.copy(localTeamId = serverTeam.id)) }
                _teamIdRemaps.tryEmit(localTeamId to serverTeam.id)
            }
            "add_team_member" -> {
                val localMemberId = op.localMemberId ?: return
                if (localMemberId >= 0) return
                val serverMember = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        com.example.billiardtracker.data.remote.dto.TeamMemberDto.serializer(),
                        responseBody,
                    )
                }.getOrNull() ?: return
                val local = teamMemberDao.getById(localMemberId) ?: return
                teamMemberDao.deleteById(localMemberId)
                teamMemberDao.upsert(
                    TeamMemberEntity(
                        id = serverMember.id,
                        teamId = serverMember.teamId,
                        displayName = serverMember.displayName,
                        phone = serverMember.phone,
                        addedAt = serverMember.addedAt,
                        serverId = serverMember.id,
                    )
                )
                // Pending outbox ops (delete_team_member) с этим локальным id.
                outboxDao.pendingOps()
                    .filter { it.localMemberId == localMemberId }
                    .forEach { outboxDao.update(it.copy(localMemberId = serverMember.id)) }
            }
            "create_tournament" -> {
                // Server response: Tournament + participants (creator + client-sent).
                val localTid = op.localTournamentId ?: return
                if (localTid >= 0) return
                val serverT = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        com.example.billiardtracker.data.remote.dto.TournamentDto.serializer(),
                        responseBody,
                    )
                }.getOrNull() ?: return
                val local = tournamentDao.getById(localTid) ?: return
                val now = System.currentTimeMillis()
                // Replace tournament: delete local + insert with server id.
                tournamentDao.deleteById(localTid)
                tournamentDao.upsert(
                    TournamentEntity(
                        id = serverT.id,
                        title = serverT.title,
                        clubId = serverT.clubId,
                        gameType = serverT.gameType,
                        moneyPerBallKop = serverT.moneyPerBallKop,
                        createdByUserId = serverT.createdByUserId,
                        refereeUserId = serverT.refereeUserId,
                        status = serverT.status,
                        startedAt = serverT.startedAt,
                        finishedAt = serverT.finishedAt,
                        lastSyncedAt = now,
                        serverId = serverT.id,
                    )
                )
                // Participants remap: local list (без creator) matches
                // server list[1..N] (первый — creator, добавленный сервером).
                val localParts = participantDao.listByTournament(localTid)
                val serverParts = serverT.participants
                // Insert creator (первый в serverParts, у нас его локально не было).
                if (serverParts.isNotEmpty()) {
                    val creator = serverParts.first()
                    participantDao.upsert(
                        ParticipantEntity(
                            id = creator.id,
                            tournamentId = serverT.id,
                            userId = creator.userId,
                            displayName = creator.displayName,
                            handicapPoints = creator.handicapPoints,
                            perBallOverrideKop = creator.perBallOverrideKop,
                            lastSyncedAt = now,
                        )
                    )
                }
                // Match каждый локальный participant к серверу по индексу
                // (сервер сохраняет порядок из body.participants + creator prepended).
                localParts.forEachIndexed { idx, local ->
                    val serverIdx = idx + 1 // skip creator
                    if (serverIdx >= serverParts.size) return@forEachIndexed
                    val serverP = serverParts[serverIdx]
                    val oldPid = local.id
                    val newPid = serverP.id
                    if (oldPid == newPid) return@forEachIndexed
                    participantDao.deleteById(oldPid)
                    participantDao.upsert(
                        ParticipantEntity(
                            id = newPid,
                            tournamentId = serverT.id,
                            userId = serverP.userId,
                            displayName = serverP.displayName,
                            handicapPoints = serverP.handicapPoints,
                            perBallOverrideKop = serverP.perBallOverrideKop,
                            lastSyncedAt = now,
                        )
                    )
                    // Cascade: games.winnerParticipantId + shots.participantId.
                    gameDao.remapWinnerParticipantId(oldPid, newPid)
                    shotDao.remapParticipantId(oldPid, newPid)
                }
                // Cascade: games.tournamentId + pending outbox ops.
                gameDao.remapTournamentId(oldTid = localTid, newTid = serverT.id)
                outboxDao.pendingOps()
                    .filter { it.localTournamentId == localTid }
                    .forEach { outboxDao.update(it.copy(localTournamentId = serverT.id)) }
            }
            "start_game" -> {
                // Сервер вернул Game{id: <serverId>}. Заменяем локальный
                // GameEntity серверным (id меняется с отрицательного на
                // положительный). Каскадно обновляем FK у shots и pending
                // outbox-ops, чтобы всё продолжало работать.
                val localGid = op.localGameId ?: return
                if (localGid >= 0) return // уже серверный, ничего не делаем
                val serverGame = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        com.example.billiardtracker.data.remote.dto.GameDto.serializer(),
                        responseBody,
                    )
                }.getOrNull() ?: return
                val local = gameDao.getById(localGid) ?: return
                gameDao.deleteById(localGid)
                gameDao.upsert(
                    com.example.billiardtracker.data.local.entity.GameEntity(
                        id = serverGame.id,
                        tournamentId = serverGame.tournamentId,
                        orderIndex = serverGame.orderIndex,
                        status = serverGame.status,
                        startedAt = serverGame.startedAt,
                        finishedAt = serverGame.finishedAt,
                        winnerParticipantId = serverGame.winnerParticipantId,
                        lastSyncedAt = System.currentTimeMillis(),
                        serverId = serverGame.id,
                    )
                )
                // Cascade: shots.gameId + pending outbox ops.localGameId.
                shotDao.remapGameId(oldGid = localGid, newGid = serverGame.id)
                outboxDao.pendingOps()
                    .filter { it.localGameId == localGid }
                    .forEach { outboxDao.update(it.copy(localGameId = serverGame.id)) }
            }
            "add_shot" -> {
                val localShotId = op.localShotId ?: return
                val serverShot = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        com.example.billiardtracker.data.remote.dto.ShotDto.serializer(),
                        responseBody,
                    )
                }.getOrNull() ?: return
                val local = shotDao.getById(localShotId) ?: return
                if (local.id == serverShot.id) return // уже совпало
                // Заменяем локальный row на серверный (Room не даёт менять PK,
                // поэтому — delete + insert).
                shotDao.deleteById(localShotId)
                shotDao.upsert(
                    com.example.billiardtracker.data.local.entity.ShotEntity(
                        id = serverShot.id,
                        gameId = serverShot.gameId,
                        participantId = serverShot.participantId,
                        kind = serverShot.kind,
                        ballNumber = serverShot.ballNumber,
                        pointsDelta = serverShot.pointsDelta,
                        ts = serverShot.ts,
                        enteredByUserId = serverShot.enteredByUserId,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                )
            }
            // "delete_shot" — ничего дополнительно не нужно: локально удалили
            // сразу при вызове, а сервер вернёт 204/200.
        }
    }
}
