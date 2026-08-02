package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: Long,
    val tournamentId: Long,
    val orderIndex: Int,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val winnerParticipantId: Long?,
    val lastSyncedAt: Long,
    // Non-null → сервер знает эту игру, id может использоваться в API URL'ах.
    // Null → offline-локальная (id отрицательный), sync ещё не завершился.
    val serverId: Long? = null,
)
