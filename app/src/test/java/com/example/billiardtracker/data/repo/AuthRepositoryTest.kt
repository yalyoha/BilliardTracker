package com.example.billiardtracker.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.NetworkModule
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: UserPrefs
    private lateinit var api: ApiService
    private lateinit var repo: AuthRepository
    private lateinit var prefsFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        prefsFile = File(
            System.getProperty("java.io.tmpdir"),
            "auth-test-${System.nanoTime()}.preferences_pb",
        )
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        prefs = UserPrefs(dataStore)

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                NetworkModule.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
        api = retrofit.create(ApiService::class.java)
        repo = AuthRepository(api, prefs)
    }

    @After
    fun tearDown() {
        server.shutdown()
        prefsFile.delete()
    }

    @Test
    fun `requestCode returns debugCode on stub mode`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"ok":true,"debugCode":"1234"}""")
                .addHeader("content-type", "application/json"),
        )
        val r = repo.requestCode("+79001112203")
        assertTrue(r.isSuccess)
        assertEquals("1234", r.getOrNull())
    }

    @Test
    fun `verify happy path stores token in prefs`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"token":"eyJ.abc.xyz","userId":42}""")
                .addHeader("content-type", "application/json"),
        )
        val r = repo.verify("+79001112203", "1234")
        assertTrue(r.isSuccess)
        assertEquals("eyJ.abc.xyz", prefs.getToken())
        assertEquals(42L, prefs.getUserId())
    }

    @Test
    fun `verify wrong code returns failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"invalid_code"}""")
                .addHeader("content-type", "application/json"),
        )
        val r = repo.verify("+79001112203", "0000")
        assertTrue(r.isFailure)
        assertNull(prefs.getToken())
    }
}
