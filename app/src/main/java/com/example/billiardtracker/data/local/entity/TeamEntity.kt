package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: Long,
    val masterTokenId: Long,
    val name: String,
    val createdAt: Long,
    val serverId: Long? = null,
)
