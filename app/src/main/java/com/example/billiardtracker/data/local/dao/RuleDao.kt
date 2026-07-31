package com.example.billiardtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.billiardtracker.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY displayName ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE slug = :slug")
    suspend fun getBySlug(slug: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<RuleEntity>)

    @Query("DELETE FROM rules WHERE slug = :slug")
    suspend fun deleteBySlug(slug: String)
}
