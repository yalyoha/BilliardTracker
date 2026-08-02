package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: Long,
    val title: String?,
    val clubId: Long?,
    val gameType: String,
    val moneyPerBallKop: Long?,
    val createdByUserId: Long,
    val refereeUserId: Long?,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val lastSyncedAt: Long,
    val serverId: Long? = null,
)
