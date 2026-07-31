package com.example.billiardtracker.di

import android.content.Context
import androidx.room.Room
import com.example.billiardtracker.data.local.AppDatabase

class AppContainer(context: Context) {
    val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "billiardtracker.db",
    ).fallbackToDestructiveMigration(false).build()

    val tournamentDao get() = db.tournamentDao()
    val participantDao get() = db.participantDao()
    val gameDao get() = db.gameDao()
    val shotDao get() = db.shotDao()
    val clubDao get() = db.clubDao()
    val ruleDao get() = db.ruleDao()
}
