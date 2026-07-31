package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val address: String?,
    val lat: Double,
    val lon: Double,
    val city: String?,
    val userAdded: Boolean,
    val addedByUserId: Long?,
    val lastSyncedAt: Long,
)
