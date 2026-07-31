package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.CreateTokenBody
import com.example.billiardtracker.data.remote.dto.TokenDto

/**
 * Master-tokens: opaque share-links so a group of friends can watch your
 * tournaments through the public web view without any auth.
 *
 * All endpoints require bearer JWT — the calling UI must ensure the user is
 * cloud-logged-in (see [com.example.billiardtracker.ui.components.CloudLoginDialog]).
 */
class TokenRepository(private val api: ApiService) {

    suspend fun list(): Result<List<TokenDto>> = try {
        val res = api.listTokens()
        if (res.isSuccessful) Result.success(res.body()?.tokens.orEmpty())
        else Result.failure(IllegalStateException("HTTP ${res.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun create(name: String?): Result<TokenDto> = try {
        val res = api.createToken(CreateTokenBody(name = name?.takeIf { it.isNotBlank() }))
        if (res.isSuccessful) Result.success(res.body()!!)
        else Result.failure(IllegalStateException("HTTP ${res.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Rotates the token string in-place; server returns the new token value. */
    suspend fun rotate(id: Long): Result<String> = try {
        val res = api.rotateToken(id)
        if (res.isSuccessful) Result.success(res.body()!!.token)
        else Result.failure(IllegalStateException("HTTP ${res.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Cascade delete — the server will also drop every tournament attached to
     * this token along with games and shots. UI must confirm with a red alert.
     */
    suspend fun delete(id: Long): Result<Unit> = try {
        val res = api.deleteToken(id)
        if (res.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("HTTP ${res.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
