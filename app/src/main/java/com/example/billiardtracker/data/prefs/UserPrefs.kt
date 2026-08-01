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
    val nameFlow: Flow<String?> = dataStore.data.map { it[NAME_LOCAL] }
    val activeTokenIdFlow: Flow<Long?> = dataStore.data.map { it[ACTIVE_TOKEN_ID] }
    val activeTeamIdFlow: Flow<Long?> = dataStore.data.map { it[ACTIVE_TEAM_ID] }
    val pendingSharedTokenFlow: Flow<String?> = dataStore.data.map { it[PENDING_SHARED_TOKEN] }

    suspend fun getToken(): String? = tokenFlow.first()
    suspend fun getUserId(): Long? = userIdFlow.first()
    suspend fun getPhone(): String? = phoneFlow.first()
    suspend fun getName(): String? = nameFlow.first()
    suspend fun getActiveTokenId(): Long? = activeTokenIdFlow.first()
    suspend fun getActiveTeamId(): Long? = activeTeamIdFlow.first()
    suspend fun getPendingSharedToken(): String? = pendingSharedTokenFlow.first()

    suspend fun setActiveTeamId(id: Long?) {
        dataStore.edit {
            if (id == null) it.remove(ACTIVE_TEAM_ID) else it[ACTIVE_TEAM_ID] = id
        }
    }

    suspend fun setPendingSharedToken(token: String?) {
        dataStore.edit {
            if (token == null) it.remove(PENDING_SHARED_TOKEN)
            else it[PENDING_SHARED_TOKEN] = token
        }
    }

    suspend fun setActiveTokenId(id: Long?) {
        dataStore.edit {
            if (id == null) it.remove(ACTIVE_TOKEN_ID) else it[ACTIVE_TOKEN_ID] = id
        }
    }

    suspend fun setAuth(token: String, userId: Long, phone: String) {
        dataStore.edit {
            it[TOKEN] = token
            it[USER_ID] = userId
            it[PHONE] = phone
        }
    }

    suspend fun setLocalProfile(phone: String?, name: String?) {
        dataStore.edit {
            if (phone != null) it[PHONE] = phone else it.remove(PHONE)
            if (name != null) it[NAME_LOCAL] = name else it.remove(NAME_LOCAL)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val TOKEN = stringPreferencesKey("token")
        private val USER_ID = longPreferencesKey("user_id")
        private val PHONE = stringPreferencesKey("phone")
        private val NAME_LOCAL = stringPreferencesKey("name_local")
        private val ACTIVE_TOKEN_ID = longPreferencesKey("active_token_id")
        private val ACTIVE_TEAM_ID = longPreferencesKey("active_team_id")
        private val PENDING_SHARED_TOKEN = stringPreferencesKey("pending_shared_token")

        fun create(context: Context): UserPrefs = UserPrefs(context.userDataStore)
    }
}
