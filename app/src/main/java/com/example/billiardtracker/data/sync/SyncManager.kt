package com.example.billiardtracker.data.sync

import com.example.billiardtracker.data.local.dao.OutboxDao
import com.example.billiardtracker.data.local.dao.ShotDao
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import com.example.billiardtracker.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val networkMonitor: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    private val http = OkHttpClient()
    private val syncMutex = Mutex()

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
            for (op in ops) {
                val ok = try { executeOne(op) } catch (_: Throwable) { false }
                if (!ok) break
            }
            runCatching { outboxDao.purgeExecuted() }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun executeOne(op: OutboxOpEntity): Boolean {
        val jwt = tokenProvider() ?: return false
        return try {
            val url = baseUrl.trimEnd('/') + "/" + op.endpoint.trimStart('/')
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
            // finish_game / finish_tournament: локальные Room-rows уже обновлены
            // на этапе enqueue; серверный ответ не привносит новых ID.
            "finish_game", "finish_tournament", "delete_shot" -> {}
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
