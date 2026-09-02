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
import kotlinx.coroutines.Dispatchers
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
    private val userPrefs: com.example.billiardtracker.data.prefs.UserPrefs? = null,
    private val devLoggerProvider: () -> com.example.billiardtracker.data.telemetry.DevLogger? = { null },
) {
    private fun devlog(action: String, ok: Boolean? = null, httpCode: Int? = null, err: String? = null, payload: Map<String, Any?>? = null) {
        devLoggerProvider()?.log(kind = "sync", action = action, ok = ok, httpCode = httpCode, err = err, payload = payload)
    }
    private val http = OkHttpClient()
    private val syncMutex = Mutex()

    // Broadcast: local id → server id. Слушатели (TeamState / ViewModels)
    // могут смигрировать своё in-memory состояние без полного refresh.
    private val _teamIdRemaps = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 32)
    val teamIdRemaps: SharedFlow<Pair<Long, Long>> = _teamIdRemaps.asSharedFlow()

    // In-memory fallback remap: если сущность заменилась (delete+insert с
    // server id) и новый op заквоился со stale local id, DAO.getById(local)
    // вернёт null и resolveEndpoint встанет в gridlock. Consult этот map,
    // когда DAO не нашёл entity. Persistent через UserPrefs (id_remap строка
    // формата "t:local:server,g:local:server,...") чтобы пережить process
    // kill между успехом create_tournament и enqueue дочернего start_game.
    private val tournamentIdRemap = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val gameIdRemap = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val participantIdRemap = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val remapPersistMutex = Mutex()

    /** Resolve local (negative) participant id → server id, если известно
     *  из cascade create_tournament. Иначе возвращает исходный id. */
    fun resolveParticipantId(pid: Long): Long =
        if (pid >= 0) pid else participantIdRemap[pid] ?: pid

    /** Resolve local (negative) tournament id → server id (если create_tournament
     *  уже синкнулся и в кэше есть remap). Используется TournamentRepository.fetchDetail
     *  чтобы после sync грузить турнир из API по правильному id. */
    fun resolveTournamentId(tid: Long): Long =
        if (tid >= 0) tid else tournamentIdRemap[tid] ?: tid

    /** Аналогично для game — GameRepository.finishGame и т.п. */
    fun resolveGameId(gid: Long): Long =
        if (gid >= 0) gid else gameIdRemap[gid] ?: gid

    private suspend fun loadPersistedRemap() {
        val raw = try { userPrefs?.getIdRemap() } catch (_: Throwable) { null } ?: return
        for (entry in raw.split(',')) {
            val parts = entry.split(':')
            if (parts.size != 3) continue
            val local = parts[1].toLongOrNull() ?: continue
            val server = parts[2].toLongOrNull() ?: continue
            when (parts[0]) {
                "t" -> tournamentIdRemap[local] = server
                "g" -> gameIdRemap[local] = server
                "p" -> participantIdRemap[local] = server
            }
        }
    }

    private suspend fun persistRemap() {
        val prefs = userPrefs ?: return
        remapPersistMutex.withLock {
            val sb = StringBuilder()
            for ((l, s) in tournamentIdRemap) {
                if (sb.isNotEmpty()) sb.append(',')
                sb.append("t:").append(l).append(':').append(s)
            }
            for ((l, s) in gameIdRemap) {
                if (sb.isNotEmpty()) sb.append(',')
                sb.append("g:").append(l).append(':').append(s)
            }
            for ((l, s) in participantIdRemap) {
                if (sb.isNotEmpty()) sb.append(',')
                sb.append("p:").append(l).append(':').append(s)
            }
            try { prefs.setIdRemap(sb.toString()) } catch (_: Throwable) { }
        }
    }

    init {
        // Триггер: как только сеть появилась — драйним очередь. Ошибки
        // ловим на месте, чтобы uncaught exception в фоновом коррутине
        // не убил app process. Dispatchers.IO — OkHttp.execute() блокирует,
        // а appScope=Main → NetworkOnMainThreadException и sync никогда не
        // проходил (юзер видит вечное ⏳). Найдено v1.20.0 dev-log'ом.
        appScope.launch(Dispatchers.IO) {
            try {
                loadPersistedRemap()
                networkMonitor.online.collect { online ->
                    if (online) runCatching { drain() }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Force-drain — можно позвать из репо сразу после enqueue. */
    fun kickDrain() {
        devlog("kickDrain")
        appScope.launch(Dispatchers.IO) { runCatching { drain() } }
    }

    private suspend fun drain() {
        if (!syncMutex.tryLock()) {
            devlog("drain-skip-locked")
            return
        }
        try {
            // Chain: create_tournament → start_game → add_shot. Успешный
            // create_tournament внутри своего onSuccess каскадно правит в DB
            // localTournamentId у зависимых outbox-ops. Но текущий цикл держит
            // in-memory snapshot ops → следующий start_game видит stale tid, а
            // сущность в DAO уже удалена по этому id → op-dep-not-ready навсегда.
            // Решение: re-fetch pending после каждого прохода, повторять пока
            // pending count уменьшается (значит cascade разблокировал что-то).
            var passIdx = 0
            var initialCount = -1
            while (true) {
                val ops = try { outboxDao.pendingOps() } catch (t: Throwable) {
                    devlog("drain-pendingOps-fail", ok = false, err = t.message)
                    return
                }
                if (initialCount < 0) initialCount = ops.size
                if (ops.isEmpty()) break
                if (passIdx == 0) devlog("drain-start", payload = mapOf("pending" to ops.size))
                val countBefore = ops.size
                for (op in ops) {
                    try { executeOne(op) } catch (t: Throwable) {
                        devlog("op-exception", ok = false, err = t.message,
                            payload = mapOf("kind" to op.kind, "opId" to op.opId))
                    }
                }
                runCatching { outboxDao.purgeExecuted() }
                val countAfter = outboxDao.pendingOps().size
                passIdx += 1
                if (countAfter >= countBefore) break // прогресса нет — оставшиеся
                                                     // ждут внешнего события
                if (passIdx >= 8) { devlog("drain-max-passes"); break } // safety
            }
            devlog("drain-end", payload = mapOf("passes" to passIdx, "initial" to initialCount))
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
        // Bypass для "self-creating" ops: они сами породят serverId, ждать его
        // до отправки — вечный gridlock. Endpoint у них не содержит соотв. id
        // (create_tournament → /api/tournaments; start_game → /.../games).
        val realTid = when {
            tid == null -> null
            tid >= 0 -> tid
            op.kind == "create_tournament" -> tid
            else -> tournamentDao.getById(tid)?.serverId
                ?: tournamentIdRemap[tid]
                ?: return null
        }
        val realGid = when {
            gid == null -> null
            gid >= 0 -> gid
            op.kind == "start_game" -> gid
            else -> gameDao.getById(gid)?.serverId
                ?: gameIdRemap[gid]
                ?: return null
        }
        // create_team не использует teamId в endpoint'е (POST /api/tokens/{tid}/teams)
        // — не блокируем op пока serverId отсутствует, иначе gridlock: sync не
        // запускает POST → serverId никогда не заполнится → ⏳ висит вечно.
        val realTeamId = if (op.kind == "create_team") teamId else when {
            teamId == null -> null
            teamId >= 0 -> teamId
            else -> teamDao.getById(teamId)?.serverId ?: return null
        }
        val tokenId = op.masterTokenId
        return when (op.kind) {
            "create_tournament" -> "api/tournaments"
            "delete_tournament" -> "api/tournaments/$realTid"
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
        val jwt = tokenProvider()
        if (jwt == null) {
            devlog("op-no-jwt", ok = false, payload = mapOf("kind" to op.kind, "opId" to op.opId))
            return false
        }
        // Пересобираем endpoint с учётом того, что local IDs (отрицательные)
        // могли получить серверные serverId — подставляем их. Если зависимость
        // ещё не синкнута — пропускаем op, retry позже.
        val resolvedEndpoint = resolveEndpoint(op)
        if (resolvedEndpoint == null) {
            devlog("op-dep-not-ready", payload = mapOf(
                "kind" to op.kind, "opId" to op.opId,
                "localTeamId" to op.localTeamId, "localTournamentId" to op.localTournamentId,
                "localGameId" to op.localGameId,
            ))
            return false
        }
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
                        devlog("op-ok", ok = true, httpCode = r.code, payload = mapOf(
                            "kind" to op.kind, "opId" to op.opId, "endpoint" to resolvedEndpoint,
                        ))
                        true
                    }
                    r.code in 400..499 && r.code != 401 && r.code != 408 && r.code != 429 -> {
                        // 4xx (кроме 401/408/429) — необратимая ошибка, дальше пытаться бессмысленно
                        outboxDao.update(op.copy(
                            executed = true,
                            lastError = "HTTP ${r.code}: rejected",
                        ))
                        devlog("op-reject", ok = false, httpCode = r.code, payload = mapOf(
                            "kind" to op.kind, "opId" to op.opId, "endpoint" to resolvedEndpoint,
                        ))
                        true
                    }
                    else -> {
                        // 5xx / network — retry позже
                        outboxDao.update(op.copy(
                            attempts = op.attempts + 1,
                            lastError = "HTTP ${r.code}",
                        ))
                        devlog("op-retry", ok = false, httpCode = r.code, payload = mapOf(
                            "kind" to op.kind, "opId" to op.opId, "attempts" to (op.attempts + 1),
                        ))
                        false
                    }
                }
            }
        } catch (e: Exception) {
            outboxDao.update(op.copy(
                attempts = op.attempts + 1,
                lastError = e.message ?: e.javaClass.simpleName,
            ))
            devlog("op-network-fail", ok = false,
                err = "${e.javaClass.simpleName}: ${e.message ?: "(no message)"}",
                payload = mapOf(
                    "kind" to op.kind, "opId" to op.opId, "attempts" to (op.attempts + 1),
                    "endpoint" to resolvedEndpoint,
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
            "delete_tournament" -> {
                // Сервер подтвердил удаление — убираем local_hidden tombstone из DB.
                val tid = op.localTournamentId ?: return
                if (tid > 0) tournamentDao.deleteById(tid)
            }
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
                devlog("team-remap", payload = mapOf("local" to localTeamId, "server" to serverTeam.id))
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
                // Маппинг и каскад outbox-ops ставим ДО проверки entity: если
                // refreshMine() вызвал deleteAll() между enqueue и onSuccess,
                // entity уже нет в базе, но remap должен быть сохранён, иначе
                // start_game застрянет с 0 попыток навсегда (resolveEndpoint → null).
                tournamentIdRemap[localTid] = serverT.id
                persistRemap()
                gameDao.remapTournamentId(oldTid = localTid, newTid = serverT.id)
                outboxDao.pendingOps()
                    .filter { it.localTournamentId == localTid }
                    .forEach { outboxDao.update(it.copy(localTournamentId = serverT.id)) }
                devlog("tournament-remap", payload = mapOf("local" to localTid, "server" to serverT.id))
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
                    // In-memory remap: add_shot приходит с participantId из
                    // ViewModel-state, который держит local pid до refresh(). См.
                    // GameRepository.addShot → SyncManager.resolveParticipantId.
                    participantIdRemap[oldPid] = newPid
                }
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
                // In-memory + persistent fallback (см. коммент для tournamentIdRemap).
                gameIdRemap[localGid] = serverGame.id
                persistRemap()
                devlog("game-remap", payload = mapOf("local" to localGid, "server" to serverGame.id))
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
