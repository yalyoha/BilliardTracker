package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val slug: String,
    val displayName: String,
    val markdown: String,
    val cachedAt: Long,
)
