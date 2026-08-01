package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.RegisterBody
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

    /**
     * No-SMS registration/login. Server upserts user by phone, updates name,
     * and returns a JWT immediately. Data correctness is confirmed by the
     * user in a client-side "всё верно?" dialog before this is called.
     */
    suspend fun register(phone: String, name: String): Result<Unit> = try {
        val res = api.register(RegisterBody(phone, name.trim()))
        if (res.isSuccessful) {
            val body = res.body()!!
            prefs.setAuth(body.token, body.userId, phone)
            prefs.setLocalProfile(phone, name.trim())
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("register failed: HTTP ${res.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Atomic onboarding: register + create the first master-token, and only
     * persist the JWT/profile/activeTokenId if BOTH succeed. Prevents the
     * "JWT saved but token creation was cancelled/failed" state that would
     * strand the user in the main app with an empty tokens list.
     */
    suspend fun registerAndCreateFirstToken(
        phone: String,
        name: String,
        pathName: String,
    ): Result<Unit> = try {
        val regRes = api.register(RegisterBody(phone, name.trim()))
        if (!regRes.isSuccessful) {
            Result.failure(IllegalStateException("register failed: HTTP ${regRes.code()}"))
        } else {
            val regBody = regRes.body()!!
            val tokRes = api.createTokenWithAuth(
                authorization = "Bearer ${regBody.token}",
                body = com.example.billiardtracker.data.remote.dto.CreateTokenBody(name = pathName),
            )
            if (!tokRes.isSuccessful) {
                Result.failure(IllegalStateException("token create failed: HTTP ${tokRes.code()}"))
            } else {
                val created = tokRes.body()!!
                prefs.setAuth(regBody.token, regBody.userId, phone)
                prefs.setLocalProfile(phone, name.trim())
                prefs.setActiveTokenId(created.id)
                Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun logout() = prefs.clear()
}
