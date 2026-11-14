package com.example.billiardtracker.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.billiardtracker.data.prefs.UserPrefs
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Регрессия 2026-09-03: JWT истёк через 30 дней, клиент бесконечно ретраил 401,
 * два месяца игр не дошли до сервера. TokenRefresher должен перерегистрироваться
 * по сохранённым phone+name и повторить запрос.
 */
class TokenRefresherTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsFile: File
    private lateinit var prefs: UserPrefs
    private val registerCalls = AtomicInteger()
    @Volatile private var registerStatus = 200

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/auth/register") {
                    registerCalls.incrementAndGet()
                    Thread.sleep(50) // чтобы параллельные 401 успели встать в очередь на mutex
                    return if (registerStatus == 200)
                        MockResponse().setBody("""{"token":"fresh","userId":30}""")
                    else MockResponse().setResponseCode(registerStatus)
                }
                return if (request.getHeader("Authorization") == "Bearer fresh")
                    MockResponse().setBody("""{"ok":true}""")
                else MockResponse().setResponseCode(401)
            }
        }
        server.start()
        prefsFile = File(System.getProperty("java.io.tmpdir"), "tokref-${System.nanoTime()}.preferences_pb")
        prefs = UserPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
        runBlocking {
            prefs.setAuth("expired", 30, "+79990000000")
            prefs.setLocalProfile("+79990000000", "Юрий")
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        prefsFile.delete()
    }

    private fun client() = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(prefs))
        .authenticator(TokenRefresher(server.url("/").toString(), prefs))
        .build()

    private fun get(c: OkHttpClient) =
        c.newCall(Request.Builder().url(server.url("/api/tournaments/mine")).build()).execute()

    @Test
    fun `401 triggers re-register and retries with new token`() {
        get(client()).use { r ->
            assertEquals(200, r.code)
        }
        assertEquals(1, registerCalls.get())
        assertEquals("fresh", runBlocking { prefs.getToken() })
        val reg = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }
            .first { it.path == "/api/auth/register" }
        val body = reg.body.readUtf8()
        assertTrue(body, body.contains("+79990000000") && body.contains("Юрий"))
    }

    @Test
    fun `parallel 401s register only once`() {
        val c = client()
        val pool = Executors.newFixedThreadPool(6)
        val codes = (1..6).map { pool.submit<Int> { get(c).use { it.code } } }.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(List(6) { 200 }, codes)
        assertEquals(1, registerCalls.get())
    }

    @Test
    fun `failed re-register surfaces 401 without looping`() {
        registerStatus = 429
        get(client()).use { r -> assertEquals(401, r.code) }
        assertEquals(1, registerCalls.get())
        assertEquals("expired", runBlocking { prefs.getToken() })
    }
}
