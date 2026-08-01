package com.example.billiardtracker.di

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
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
    val teamState = com.example.billiardtracker.ui.nav.TeamState()

    /**
     * Application-lifetime scope for multi-step flows that must survive UI
     * teardown (e.g. onboarding register+createToken: register puts JWT into
     * prefs → composition re-renders → OnboardingScreen leaves → its
     * rememberCoroutineScope cancels the in-flight createToken. Using this
     * scope decouples completion from composition.)
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Guards the "auto-create default path master when list is empty" branch
     * of SettingsViewModel.refreshTokens. Two overlapping VM inits (bottom-nav
     * recomposition) each seeing an empty list would race and each POST a
     * new token — user ended up with two paths after deleting all.
     */
    val tokenSelfHealMutex: Mutex = Mutex()
}
