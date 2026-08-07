package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.RegisterBody
import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.VerifyBody
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: ApiService,
    private val prefs: UserPrefs,
    private val devLoggerProvider: () -> com.example.billiardtracker.data.telemetry.DevLogger? = { null },
) {
    val isAuthed: Flow<Boolean> = prefs.tokenFlow.map { !it.isNullOrEmpty() }

    private fun devlog(action: String, ok: Boolean? = null, httpCode: Int? = null, err: String? = null, payload: Map<String, Any?>? = null) {
        devLoggerProvider()?.log(kind = "auth", action = action, ok = ok, httpCode = httpCode, err = err, payload = payload)
    }

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
        devlog("registerAndCreateFirstToken:in", payload = mapOf("phone" to phone, "name" to name))
        val regRes = api.register(RegisterBody(phone, name.trim()))
        if (!regRes.isSuccessful) {
            devlog("register:fail", ok = false, httpCode = regRes.code())
            Result.failure(IllegalStateException("register failed: HTTP ${regRes.code()}"))
        } else {
            val regBody = regRes.body()!!
            devlog("register:ok", ok = true, httpCode = regRes.code(), payload = mapOf("userId" to regBody.userId))
            val tokRes = api.createTokenWithAuth(
                authorization = "Bearer ${regBody.token}",
                body = com.example.billiardtracker.data.remote.dto.CreateTokenBody(name = pathName),
            )
            if (!tokRes.isSuccessful) {
                devlog("createFirstToken:fail", ok = false, httpCode = tokRes.code())
                Result.failure(IllegalStateException("token create failed: HTTP ${tokRes.code()}"))
            } else {
                val created = tokRes.body()!!
                // NonCancellable: prefs.setAuth эмитит tokenFlow → isAuthed=true →
                // nav-swap Onboarding→Main → rememberCoroutineScope в OnboardingScreen
                // отменяется В СЕРЕДИНЕ 3-х suspend prefs writes, оставляя
                // activeTokenId=null. Юзер потом не может создать команду
                // (addTeam:no-token). Всё три write'а атомарно вне cancellation.
                withContext(NonCancellable) {
                    prefs.setAuth(regBody.token, regBody.userId, phone)
                    prefs.setLocalProfile(phone, name.trim())
                    prefs.setActiveTokenId(created.id)
                }
                devlog("createFirstToken:ok", ok = true, httpCode = tokRes.code(), payload = mapOf("tokenId" to created.id))
                Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        devlog("registerAndCreateFirstToken:exception", ok = false, err = e.message)
        Result.failure(e)
    }

    suspend fun logout() = prefs.clear()
}
