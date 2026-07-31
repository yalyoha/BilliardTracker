package com.example.billiardtracker.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double)

class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<GeoPoint?> = try {
        if (!hasPermission()) {
            Result.success(null)
        } else {
            val loc: android.location.Location? = suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            Result.success(loc?.let { GeoPoint(it.latitude, it.longitude) })
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
