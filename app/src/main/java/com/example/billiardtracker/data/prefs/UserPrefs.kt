package com.example.billiardtracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore("user_prefs")

class UserPrefs(private val dataStore: DataStore<Preferences>) {
    val tokenFlow: Flow<String?> = dataStore.data.map { it[TOKEN] }
    val userIdFlow: Flow<Long?> = dataStore.data.map { it[USER_ID] }
    val phoneFlow: Flow<String?> = dataStore.data.map { it[PHONE] }

    suspend fun getToken(): String? = tokenFlow.first()
    suspend fun getUserId(): Long? = userIdFlow.first()

    suspend fun setAuth(token: String, userId: Long, phone: String) {
        dataStore.edit {
            it[TOKEN] = token
            it[USER_ID] = userId
            it[PHONE] = phone
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val TOKEN = stringPreferencesKey("token")
        private val USER_ID = longPreferencesKey("user_id")
        private val PHONE = stringPreferencesKey("phone")

        fun create(context: Context): UserPrefs = UserPrefs(context.userDataStore)
    }
}
