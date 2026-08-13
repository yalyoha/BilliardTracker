package com.example.billiardtracker.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Persistent id-remap must survive process kill between successful
 * create_tournament (server 201 → in-memory tournamentIdRemap[localTid] = serverId)
 * and enqueue of the dependent child op (start_game) that still references localTid.
 * See SyncManager.persistRemap / loadPersistedRemap. Regression: v1.21.0 lost this
 * mapping on cold start and the child op sat in outbox forever (⏳ badge grew).
 */
class IdRemapPersistTest {
    private lateinit var prefsFile: File
    private lateinit var prefs: UserPrefs

    @Before
    fun setUp() {
        prefsFile = File(
            System.getProperty("java.io.tmpdir"),
            "idremap-test-${System.nanoTime()}.preferences_pb",
        )
        val ds = PreferenceDataStoreFactory.create(produceFile = { prefsFile })
        prefs = UserPrefs(ds)
    }

    @After
    fun tearDown() {
        prefsFile.delete()
    }

    @Test
    fun `default remap is null`() = runBlocking {
        assertNull(prefs.getIdRemap())
    }

    @Test
    fun `set then get returns the stored string`() = runBlocking {
        prefs.setIdRemap("t:-1:42,g:-7:99,p:-3:17")
        assertEquals("t:-1:42,g:-7:99,p:-3:17", prefs.getIdRemap())
    }

    @Test
    fun `empty string clears the entry (semantically null)`() = runBlocking {
        prefs.setIdRemap("t:-1:42")
        prefs.setIdRemap("")
        assertNull(prefs.getIdRemap())
    }

    @Test
    fun `null clears the entry`() = runBlocking {
        prefs.setIdRemap("t:-1:42")
        prefs.setIdRemap(null)
        assertNull(prefs.getIdRemap())
    }

    @Test
    fun `overwrite replaces prior mapping`() = runBlocking {
        prefs.setIdRemap("t:-1:42")
        prefs.setIdRemap("t:-2:55,g:-3:66")
        assertEquals("t:-2:55,g:-3:66", prefs.getIdRemap())
    }
}
