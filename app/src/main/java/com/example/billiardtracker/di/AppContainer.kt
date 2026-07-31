package com.example.billiardtracker.di

import android.content.Context
import androidx.room.Room
import com.example.billiardtracker.data.local.AppDatabase
import com.example.billiardtracker.data.prefs.UpdatePrefs
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.NetworkModule
import com.example.billiardtracker.data.remote.SseClient
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.data.repo.ClubRepository
import com.example.billiardtracker.data.repo.DonationRepository
import com.example.billiardtracker.data.repo.GameRepository
import com.example.billiardtracker.data.repo.RuleRepository
import com.example.billiardtracker.data.repo.TokenRepository
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.data.repo.UpdaterRepository

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

    val userPrefs: UserPrefs = UserPrefs.create(context.applicationContext)

    val retrofit = NetworkModule.provideRetrofit(
        baseUrl = "https://billiardtracker.alekseylosev.ru/",
        userPrefs = userPrefs,
    )
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    val authRepository = AuthRepository(apiService, userPrefs)
    val tournamentRepository = TournamentRepository(apiService, tournamentDao, participantDao)
    val gameRepository = GameRepository(apiService)
    val ruleRepository = RuleRepository(apiService, ruleDao)
    val updaterRepository = UpdaterRepository(apiService)
    val clubRepository = ClubRepository(apiService)
    val donationRepository = DonationRepository(apiService)
    val tokenRepository = TokenRepository(apiService)
    val locationProvider = com.example.billiardtracker.data.location.LocationProvider(context.applicationContext)
    val detectClubUseCase = com.example.billiardtracker.domain.usecase.DetectClubUseCase(locationProvider, clubRepository)
    val updatePrefs: UpdatePrefs = UpdatePrefs.create(context.applicationContext)
    val sseClient = SseClient(
        baseUrl = "https://billiardtracker.alekseylosev.ru/",
        prefs = userPrefs,
    )

    val newTournamentState = com.example.billiardtracker.ui.nav.NewTournamentState()
}
