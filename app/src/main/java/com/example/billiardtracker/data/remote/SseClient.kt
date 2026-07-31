package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.prefs.UserPrefs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

data class SseEvent(val type: String, val data: String)

class SseClient(private val baseUrl: String, private val prefs: UserPrefs) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    fun stream(tournamentId: Long): Flow<SseEvent> = callbackFlow {
        val token = runBlocking { prefs.getToken() }
        val req = Request.Builder()
            .url("${baseUrl}api/tournaments/$tournamentId/stream")
            .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                trySend(SseEvent(type ?: "message", data))
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                close(t)
            }
        }
        val source = EventSources.createFactory(client).newEventSource(req, listener)
        awaitClose { source.cancel() }
    }
}
