package com.example.billiardtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Отложенная операция для синхронизации с сервером. Все локальные write'ы
 * сначала пишутся в Room + enqueue в этот outbox; SyncWorker потом драйнит
 * очередь при появлении сети.
 *
 * kind = имя операции; payloadJson = сериализованный body запроса
 * (уже с подставленными server-id'ами когда возможно, иначе с локальными
 * и sync-worker дорезолвит перед отправкой).
 */
@Entity(tableName = "outbox_ops")
data class OutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val kind: String,             // напр. "add_shot", "delete_shot", "start_game", "finish_game", "create_tournament", "finish_tournament", "transfer_referee", "claim_referee"
    val payloadJson: String,      // JSON тела запроса
    val endpoint: String,         // URL с параметрами, напр. "api/games/42/shots"
    val method: String,           // POST / DELETE / PATCH
    val localTournamentId: Long? = null,  // локальные ID для dep resolution (если тот же outbox содержит create_tournament — сначала он)
    val localGameId: Long? = null,
    val localShotId: Long? = null,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val executed: Boolean = false,  // помечается true после успеха; строку не удаляем сразу для аудита + возможного отката
)
