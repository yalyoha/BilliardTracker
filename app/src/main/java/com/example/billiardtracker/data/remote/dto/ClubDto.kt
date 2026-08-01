package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClubDto(
    val id: Long,
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
    val userAdded: Int = 0,
    val addedByUserId: Long? = null,
    val distanceM: Int? = null,
)

@Serializable
data class ClubsListDto(val clubs: List<ClubDto>)

@Serializable
data class CreateClubBody(
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
)

@Serializable
data class UpdateClubBody(
    val name: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val city: String? = null,
)
