package com.example.billiardtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.billiardtracker.data.local.dao.ClubDao
import com.example.billiardtracker.data.local.dao.GameDao
import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.dao.RuleDao
import com.example.billiardtracker.data.local.dao.ShotDao
import com.example.billiardtracker.data.local.dao.TournamentDao
import com.example.billiardtracker.data.local.entity.ClubEntity
import com.example.billiardtracker.data.local.entity.GameEntity
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.RuleEntity
import com.example.billiardtracker.data.local.entity.ShotEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity

@Database(
    entities = [
        TournamentEntity::class,
        ParticipantEntity::class,
        GameEntity::class,
        ShotEntity::class,
        ClubEntity::class,
        RuleEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tournamentDao(): TournamentDao
    abstract fun participantDao(): ParticipantDao
    abstract fun gameDao(): GameDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
    abstract fun ruleDao(): RuleDao
}
