package com.example.billiardtracker.data.telemetry

import android.os.Build
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.DevLogBatchBody
import com.example.billiardtracker.data.remote.dto.DevLogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Dev-only телеметрия. Собираем в оффер, батчами POST-им на /api/dev-log.
 *
 *  - fire-and-forget: любые ошибки сети/парсинга поглощаются, никогда не роняем app.
 *  - session — UUID на жизнь процесса; помогает отличать перезапуски.
 *  - device — Build.MANUFACTURER + MODEL + Android version.
 *  - user_id — snapshot из prefs при отправке батча (лениво, чтобы залогинившийся
 *    юзер сразу появлялся в логах).
 *
 * Флаг включения — BuildConfig.ENABLE_DEV_LOG (сейчас всегда true).
 */
class DevLogger(
    private val api: ApiService,
    private val prefs: UserPrefs,
    private val appScope: CoroutineScope,
    private val enabled: Boolean,
) {
    private val session: String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    private val device: String = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"
    private val queue = Channel<DevLogEvent>(capacity = 500)
    private var flusher: Job? = null

    init {
        if (enabled) {
            flusher = appScope.launch { runFlusher() }
        }
    }

    fun log(
        kind: String,
        action: String,
        screen: String? = null,
        payload: Map<String, Any?>? = null,
        ok: Boolean? = null,
        httpCode: Int? = null,
        err: String? = null,
    ) {
        if (!enabled) return
        val event = DevLogEvent(
            ts = System.currentTimeMillis(),
            session = session,
            device = device,
            kind = kind,
            screen = screen,
            action = action,
            payload = payload?.let { toJson(it) },
            ok = ok,
            http_code = httpCode,
            err = err,
        )
        // trySend не блокирует; при переполнении просто дропаем — телеметрия не должна тормозить app.
        queue.trySend(event)
    }

    private suspend fun runFlusher() {
        val buf = mutableListOf<DevLogEvent>()
        while (true) {
            // Сначала блокируемся на первом event'е, потом дренаж без ожидания.
            val first = queue.receive()
            buf.add(first)
            drainQueue(buf)
            if (buf.size < BATCH_MIN) {
                // Ждём чтобы набрать больше или таймаута хватило.
                delay(FLUSH_DELAY_MS)
                drainQueue(buf)
            }
            flush(buf)
            buf.clear()
        }
    }

    private fun drainQueue(buf: MutableList<DevLogEvent>) {
        while (buf.size < BATCH_MAX) {
            val r = queue.tryReceive()
            val e = r.getOrNull() ?: break
            buf.add(e)
        }
    }

    private suspend fun flush(events: List<DevLogEvent>) {
        if (events.isEmpty()) return
        // Проставляем актуальный user_id (может залогиниться между сборкой event'а и flush'ем).
        val userId = runCatching { prefs.getUserId() }.getOrNull()
        val toSend = if (userId == null) events else events.map { it.copy(user_id = userId) }
        runCatching { api.sendDevLog(DevLogBatchBody(toSend)) }
    }

    private fun toJson(map: Map<String, Any?>): JsonElement {
        val entries = LinkedHashMap<String, JsonElement>()
        for ((k, v) in map) {
            entries[k] = when (v) {
                null -> JsonPrimitive(null as String?)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is String -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        }
        return JsonObject(entries)
    }

    companion object {
        private const val BATCH_MIN = 5
        private const val BATCH_MAX = 50
        private const val FLUSH_DELAY_MS = 2000L
    }
}
