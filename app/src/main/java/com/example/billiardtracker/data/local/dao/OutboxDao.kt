package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(op: OutboxOpEntity): Long

    @Update
    suspend fun update(op: OutboxOpEntity)

    @Query("SELECT * FROM outbox_ops WHERE executed = 0 ORDER BY opId ASC")
    suspend fun pendingOps(): List<OutboxOpEntity>

    /** Реактивно наблюдать количество pending — для UI-индикатора «⏳». */
    @Query("SELECT COUNT(*) FROM outbox_ops WHERE executed = 0")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox_ops WHERE opId = :opId")
    suspend fun deleteById(opId: Long)

    @Query("DELETE FROM outbox_ops WHERE executed = 1")
    suspend fun purgeExecuted()
}
