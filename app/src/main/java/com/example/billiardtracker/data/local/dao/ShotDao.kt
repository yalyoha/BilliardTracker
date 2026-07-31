package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.ShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {
    @Query("SELECT * FROM shots WHERE gameId = :gameId ORDER BY ts ASC")
    fun observeByGame(gameId: Long): Flow<List<ShotEntity>>

    @Query("SELECT * FROM shots WHERE id = :id")
    suspend fun getById(id: Long): ShotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ShotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ShotEntity)

    @Query("DELETE FROM shots WHERE id = :id")
    suspend fun deleteById(id: Long)
}
