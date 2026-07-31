package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentDto(
    val id: Long,
    val title: String? = null,
    val clubId: Long? = null,
    val gameType: String,
    val moneyPerBallKop: Long? = null,
    val createdByUserId: Long,
    val refereeUserId: Long? = null,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val participants: List<ParticipantDto> = emptyList(),
)

@Serializable
data class ParticipantDto(
    val id: Long,
    val userId: Long? = null,
    val displayName: String,
    val handicapPoints: Int,
    val perBallOverrideKop: Long? = null,
)

@Serializable
data class TournamentSummaryDto(
    val id: Long,
    val title: String? = null,
    val gameType: String,
    val status: String,
    val startedAt: Long,
    val participantCount: Int,
)

@Serializable
data class TournamentsListDto(
    val tournaments: List<TournamentSummaryDto>,
)

@Serializable
data class CreateTournamentBody(
    val title: String? = null,
    val clubId: Long? = null,
    val gameType: String,
    val moneyPerBallKop: Long? = null,
    val participants: List<CreateParticipantBody> = emptyList(),
)

@Serializable
data class CreateParticipantBody(
    val userId: Long? = null,
    val phone: String? = null,
    val displayName: String,
    val handicapPoints: Int = 0,
    val perBallOverrideKop: Long? = null,
)
