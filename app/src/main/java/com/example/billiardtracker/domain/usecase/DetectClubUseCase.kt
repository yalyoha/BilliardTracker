package com.example.billiardtracker.domain.usecase

import com.example.billiardtracker.data.location.GeoPoint
import com.example.billiardtracker.data.location.LocationProvider
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.data.repo.ClubRepository

class DetectClubUseCase(
    private val locationProvider: LocationProvider,
    private val clubRepo: ClubRepository,
) {
    data class Detection(val myLocation: GeoPoint?, val nearestClub: ClubDto?)

    /**
     * Returns closest club within 200m of user's current location.
     * Returns Detection(null, null) if no permission.
     * Returns Detection(loc, null) if location known but no club within 200m.
     */
    suspend operator fun invoke(radiusM: Int = 1000): Result<Detection> {
        val locResult = locationProvider.getCurrentLocation()
        val loc = locResult.getOrNull() ?: return Result.success(Detection(null, null))
        val clubsResult = clubRepo.listNear(loc.lat, loc.lon, radiusM = radiusM)
        val nearest = clubsResult.getOrNull()?.firstOrNull()
        return Result.success(Detection(loc, nearest))
    }
}
