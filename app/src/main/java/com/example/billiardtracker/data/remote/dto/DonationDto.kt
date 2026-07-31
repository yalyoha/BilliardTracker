package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateDonationBody(
    val tournamentId: Long,
    val gameId: Long? = null,
    val amountKop: Long,
    val percent: Int? = null,
    val comment: String? = null,
    val winnerUserId: Long? = null,
)

@Serializable
data class DonationDto(val id: Long, val status: String)
