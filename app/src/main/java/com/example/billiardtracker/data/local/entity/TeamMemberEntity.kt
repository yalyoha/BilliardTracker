package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey val id: Long,
    val teamId: Long,
    val displayName: String,
    val phone: String?,
    val addedAt: Long,
    val serverId: Long? = null,
)
