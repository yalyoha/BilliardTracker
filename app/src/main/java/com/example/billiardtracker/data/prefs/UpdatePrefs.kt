package com.example.billiardtracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore("update_prefs")

class UpdatePrefs(private val dataStore: DataStore<Preferences>) {
    private val AUTO_CHECK = booleanPreferencesKey("update_auto_check")
    private val SKIP_VERSION_CODE = intPreferencesKey("update_skip_version_code")

    val autoCheckFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_CHECK] ?: true }
    val skipVersionCodeFlow: Flow<Int> = dataStore.data.map { it[SKIP_VERSION_CODE] ?: 0 }

    suspend fun getAutoCheck(): Boolean = autoCheckFlow.first()
    suspend fun getSkipVersionCode(): Int = skipVersionCodeFlow.first()

    suspend fun setAutoCheck(v: Boolean) { dataStore.edit { it[AUTO_CHECK] = v } }
    suspend fun setSkipVersionCode(code: Int) { dataStore.edit { it[SKIP_VERSION_CODE] = code } }

    companion object {
        fun create(context: Context): UpdatePrefs = UpdatePrefs(context.updateDataStore)
    }
}
