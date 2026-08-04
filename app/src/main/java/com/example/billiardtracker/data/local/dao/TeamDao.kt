package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.TeamEntity
import com.example.billiardtracker.data.local.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE masterTokenId = :tokenId ORDER BY createdAt ASC")
    fun observeByToken(tokenId: Long): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: Long): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TeamEntity>)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM teams WHERE masterTokenId = :tokenId AND serverId IS NOT NULL")
    suspend fun deleteAllSyncedForToken(tokenId: Long)

    @Query("SELECT * FROM teams WHERE masterTokenId = :tokenId ORDER BY createdAt ASC")
    suspend fun listByToken(tokenId: Long): List<TeamEntity>
}

@Dao
interface TeamMemberDao {
    @Query("SELECT * FROM team_members WHERE teamId = :teamId ORDER BY id ASC")
    fun observeByTeam(teamId: Long): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members WHERE teamId IN (:teamIds) ORDER BY id ASC")
    fun observeByTeams(teamIds: List<Long>): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members WHERE teamId = :teamId ORDER BY id ASC")
    suspend fun listByTeam(teamId: Long): List<TeamMemberEntity>

    @Query("SELECT * FROM team_members WHERE id = :id")
    suspend fun getById(id: Long): TeamMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TeamMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TeamMemberEntity>)

    @Query("DELETE FROM team_members WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM team_members WHERE teamId = :teamId")
    suspend fun deleteByTeam(teamId: Long)

    /** После sync create_team — переназначить FK у существующих members. */
    @Query("UPDATE team_members SET teamId = :newTid WHERE teamId = :oldTid")
    suspend fun remapTeamId(oldTid: Long, newTid: Long)
}
