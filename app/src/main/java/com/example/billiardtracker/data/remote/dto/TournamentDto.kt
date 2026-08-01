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
    val winsRequired: Int? = null,
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
    val masterTokenId: Long? = null,
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
    val winsRequired: Int? = null,
    val masterTokenId: Long? = null,
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

@Serializable
data class GameDto(
    val id: Long,
    val tournamentId: Long,
    val orderIndex: Int,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val winnerParticipantId: Long? = null,
    val scores: List<ScoreDto> = emptyList(),
)

@Serializable
data class ScoreDto(val participantId: Long, val points: Int)

@Serializable
data class GamesListDto(val games: List<GameDto>)

@Serializable
data class ShotDto(
    val id: Long,
    val gameId: Long,
    val participantId: Long,
    val kind: String,
    val ballNumber: Int? = null,
    val pointsDelta: Int,
    val ts: Long,
    val enteredByUserId: Long,
)

@Serializable
data class ShotsListDto(val shots: List<ShotDto>)

@Serializable
data class CreateShotBody(
    val participantId: Long,
    val kind: String,
    val ballNumber: Int? = null,
    val pointsDelta: Int,
)

@Serializable
data class FinishGameBody(val winnerParticipantId: Long? = null)

@Serializable
data class ClaimRefereeResponse(
    val id: Long,
    val refereeUserId: Long,
    val createdByUserId: Long,
    val status: String,
)
