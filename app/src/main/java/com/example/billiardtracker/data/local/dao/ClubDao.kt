package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.ClubEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {
    @Query("SELECT * FROM clubs ORDER BY name ASC")
    fun observeAll(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs WHERE id = :id")
    suspend fun getById(id: Long): ClubEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ClubEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ClubEntity)

    @Query("DELETE FROM clubs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
