package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_ops")
data class OutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val kind: String,
    val payloadJson: String,
    val endpoint: String,
    val method: String,
    val localTournamentId: Long? = null,
    val localGameId: Long? = null,
    val localShotId: Long? = null,
    val localTeamId: Long? = null,
    val localMemberId: Long? = null,
    val masterTokenId: Long? = null,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val executed: Boolean = false,
)
