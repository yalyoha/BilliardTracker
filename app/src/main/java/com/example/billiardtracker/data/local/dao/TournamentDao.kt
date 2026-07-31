package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.TournamentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getById(id: Long): TournamentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TournamentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TournamentEntity)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
