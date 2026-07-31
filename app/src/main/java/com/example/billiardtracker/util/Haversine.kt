package com.example.billiardtracker.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val R_EARTH_M = 6_371_000.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R_EARTH_M * 2 * atan2(sqrt(a), sqrt(1 - a))
}
