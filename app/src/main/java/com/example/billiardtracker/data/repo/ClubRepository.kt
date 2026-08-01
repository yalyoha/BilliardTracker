package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.data.remote.dto.CreateClubBody
import com.example.billiardtracker.data.remote.dto.UpdateClubBody

class ClubRepository(private val api: ApiService) {
    suspend fun listNear(lat: Double, lon: Double, radiusM: Int = 200): Result<List<ClubDto>> = try {
        val r = api.listClubs(near = "$lat,$lon", radiusM = radiusM)
        if (r.isSuccessful) {
            Result.success(r.body()!!.clubs)
        } else {
            Result.failure(IllegalStateException("HTTP ${r.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun listAll(): Result<List<ClubDto>> = try {
        val r = api.listClubs()
        if (r.isSuccessful) {
            Result.success(r.body()!!.clubs)
        } else {
            Result.failure(IllegalStateException("HTTP ${r.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun create(body: CreateClubBody): Result<ClubDto> = try {
        val r = api.createClub(body)
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun update(id: Long, body: UpdateClubBody): Result<ClubDto> = try {
        val r = api.updateClub(id, body)
        if (r.isSuccessful) Result.success(r.body()!!)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun delete(id: Long): Result<Unit> = try {
        val r = api.deleteClub(id)
        if (r.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException("HTTP ${r.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
