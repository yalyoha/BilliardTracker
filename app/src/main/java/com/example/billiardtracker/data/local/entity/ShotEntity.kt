package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shots")
data class ShotEntity(
    @PrimaryKey val id: Long,
    val gameId: Long,
    val participantId: Long,
    val kind: String,
    val ballNumber: Int?,
    val pointsDelta: Int,
    val ts: Long,
    val enteredByUserId: Long,
    val lastSyncedAt: Long,
)
