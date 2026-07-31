package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.VersionDto

class UpdaterRepository(private val api: ApiService) {
    suspend fun fetchLatest(): Result<VersionDto> = try {
        val r = api.getVersionJson()
        if (r.isSuccessful) Result.success(r.body()!!) else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) { Result.failure(e) }
}
