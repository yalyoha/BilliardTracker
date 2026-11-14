package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.dto.RegisterBody
import com.example.billiardtracker.data.remote.dto.VerifyResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * Автоматическое обновление JWT при 401.
 *
 * Сервер выдаёт токен на ограниченный срок и не умеет refresh, а регистрация
 * без SMS — идемпотентный upsert по телефону (тот же userId). Поэтому при 401
 * просто повторяем /api/auth/register с сохранёнными phone+name, сохраняем
 * новый токен и повторяем исходный запрос.
 *
 * Без этого истёкший токен навсегда ломал синхронизацию: outbox бесконечно
 * ретраил 401 (инцидент 2026-09-08, user 30).
 */
class TokenRefresher(
    private val baseUrl: String,
    private val prefs: UserPrefs,
    private val onEvent: (action: String, ok: Boolean, httpCode: Int?) -> Unit = { _, _, _ -> },
) : Authenticator {

    // Отдельный «голый» клиент — без AuthInterceptor и без самого себя в authenticator.
    private val plain = OkHttpClient()
    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Не зацикливаемся: если уже пробовали с обновлённым токеном — сдаёмся.
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.startsWith("/api/auth/")) return null

        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = runBlocking { refreshIfStale(sentToken) } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }

    /**
     * Возвращает актуальный токен. Если другой поток уже обновил токен, пока мы
     * ждали mutex, — отдаём его без повторной регистрации (rate limit 5/мин).
     */
    suspend fun refreshIfStale(staleToken: String?): String? = mutex.withLock {
        val current = prefs.getToken()
        if (!current.isNullOrEmpty() && current != staleToken) return@withLock current

        val phone = prefs.getPhone()
        val name = prefs.getName()?.takeIf { it.isNotBlank() } ?: phone
        if (phone.isNullOrBlank() || name.isNullOrBlank()) {
            onEvent("token-refresh:no-profile", false, null)
            return@withLock null
        }
        try {
            val body = NetworkModule.json.encodeToString(RegisterBody.serializer(), RegisterBody(phone, name))
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/auth/register")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            plain.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    onEvent("token-refresh:fail", false, r.code)
                    return@withLock null
                }
                val parsed = NetworkModule.json.decodeFromString(VerifyResponse.serializer(), r.body!!.string())
                prefs.setAuth(parsed.token, parsed.userId, phone)
                onEvent("token-refresh:ok", true, r.code)
                parsed.token
            }
        } catch (e: Exception) {
            onEvent("token-refresh:exception", false, null)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var n = 1
        var prior = response.priorResponse
        while (prior != null) { n++; prior = prior.priorResponse }
        return n
    }
}
