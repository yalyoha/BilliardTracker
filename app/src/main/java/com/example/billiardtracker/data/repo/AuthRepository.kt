package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.VerifyBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val api: ApiService,
    private val prefs: UserPrefs,
) {
    val isAuthed: Flow<Boolean> = prefs.tokenFlow.map { !it.isNullOrEmpty() }

    suspend fun requestCode(phone: String): Result<String?> = try {
        val res = api.requestCode(RequestCodeBody(phone))
        if (res.isSuccessful) Result.success(res.body()?.debugCode)
        else Result.failure(IllegalStateException("request-code failed: HTTP ${res.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun verify(phone: String, code: String): Result<Unit> = try {
        val res = api.verify(VerifyBody(phone, code))
        if (res.isSuccessful) {
            val body = res.body()!!
            prefs.setAuth(body.token, body.userId, phone)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("verify failed: HTTP ${res.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun logout() = prefs.clear()
}
