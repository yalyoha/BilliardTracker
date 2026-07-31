package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.CreateDonationBody
import com.example.billiardtracker.data.remote.dto.DonationDto

class DonationRepository(private val api: ApiService) {
    suspend fun create(body: CreateDonationBody): Result<DonationDto> = try {
        val r = api.createDonation(body)
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
