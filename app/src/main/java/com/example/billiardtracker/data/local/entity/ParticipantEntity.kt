package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val id: Long,
    val tournamentId: Long,
    val userId: Long?,
    val displayName: String,
    val handicapPoints: Int,
    val perBallOverrideKop: Long?,
    val lastSyncedAt: Long,
)
