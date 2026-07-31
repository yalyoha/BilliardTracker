package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE tournamentId = :tournamentId ORDER BY orderIndex ASC")
    fun observeByTournament(tournamentId: Long): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<GameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)
}
