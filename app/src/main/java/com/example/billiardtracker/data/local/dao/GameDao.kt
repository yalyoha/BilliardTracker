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

    /** После sync create_tournament — переназначить FK у всех игр этого турнира. */
    @Query("UPDATE games SET tournamentId = :newTid WHERE tournamentId = :oldTid")
    suspend fun remapTournamentId(oldTid: Long, newTid: Long)

    /** После sync create_tournament — winner тоже может ссылаться на переехавших participants. */
    @Query("UPDATE games SET winnerParticipantId = :newPid WHERE winnerParticipantId = :oldPid")
    suspend fun remapWinnerParticipantId(oldPid: Long, newPid: Long)
}
