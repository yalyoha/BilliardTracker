package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsMeDto(
    val tournaments: TournamentsBreakdown,
    val games: GamesBreakdown,
    val score: ScoreBreakdown,
) {
    @Serializable
    data class TournamentsBreakdown(val total: Int, val active: Int, val finished: Int)

    @Serializable
    data class GamesBreakdown(val played: Int, val won: Int, val winRate: Double)

    @Serializable
    data class ScoreBreakdown(val totalBalls: Int, val avgPerGame: Double)
}
