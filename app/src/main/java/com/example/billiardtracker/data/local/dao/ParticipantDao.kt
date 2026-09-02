package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants WHERE tournamentId = :tournamentId ORDER BY id ASC")
    fun observeByTournament(tournamentId: Long): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE id = :id")
    suspend fun getById(id: Long): ParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ParticipantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ParticipantEntity)

    @Query("DELETE FROM participants WHERE tournamentId = :tournamentId")
    suspend fun deleteByTournament(tournamentId: Long)

    @Query("SELECT * FROM participants WHERE tournamentId = :tournamentId ORDER BY id ASC")
    suspend fun listByTournament(tournamentId: Long): List<ParticipantEntity>

    @Query("DELETE FROM participants WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM participants")
    suspend fun listAll(): List<ParticipantEntity>
}
