package com.example.billiardtracker.data.telemetry

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Пишет http-события в DevLogger. Игнорирует сами POST /api/dev-log и GET /dev-logs
 * чтобы не устраивать рекурсию логов о логах.
 */
class DevLogInterceptor(private val loggerProvider: () -> DevLogger?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url.encodedPath
        if (url.contains("/api/dev-log") || url.startsWith("/dev-logs")) {
            return chain.proceed(req)
        }
        val start = System.currentTimeMillis()
        val logger = loggerProvider()
        return try {
            val res = chain.proceed(req)
            val dur = System.currentTimeMillis() - start
            logger?.log(
                kind = "http",
                action = "${req.method} $url",
                ok = res.isSuccessful,
                httpCode = res.code,
                payload = mapOf("ms" to dur, "host" to req.url.host),
            )
            res
        } catch (e: IOException) {
            val dur = System.currentTimeMillis() - start
            logger?.log(
                kind = "http",
                action = "${req.method} $url",
                ok = false,
                err = e.javaClass.simpleName + ": " + (e.message ?: ""),
                payload = mapOf("ms" to dur, "host" to req.url.host),
            )
            throw e
        }
    }
}
