package com.example.billiardtracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    // ----- Room + migrations -----
    private val migration1to2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS outbox_ops (
                    opId              INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind              TEXT    NOT NULL,
                    payloadJson       TEXT    NOT NULL,
                    endpoint          TEXT    NOT NULL,
                    method            TEXT    NOT NULL,
                    localTournamentId INTEGER,
                    localGameId       INTEGER,
                    localShotId       INTEGER,
                    createdAt         INTEGER NOT NULL,
                    attempts          INTEGER NOT NULL,
                    lastError         TEXT,
                    executed          INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val migration2to3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE games ADD COLUMN serverId INTEGER")
            db.execSQL("UPDATE games SET serverId = id")
            db.execSQL("ALTER TABLE tournaments ADD COLUMN serverId INTEGER")
            db.execSQL("UPDATE tournaments SET serverId = id")
        }
    }

    val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "billiardtracker.db",
    )
        .addMigrations(migration1to2, migration2to3)
        .fallbackToDestructiveMigration(true)
        .build()

    val tournamentDao get() = db.tournamentDao()
    val participantDao get() = db.participantDao()
    val gameDao get() = db.gameDao()
    val shotDao get() = db.shotDao()
    val clubDao get() = db.clubDao()
    val ruleDao get() = db.ruleDao()
    val outboxDao get() = db.outboxDao()

    // ----- Infrastructure -----
    val networkMonitor: com.example.billiardtracker.data.sync.NetworkMonitor =
        com.example.billiardtracker.data.sync.NetworkMonitor(context.applicationContext)

    val userPrefs: UserPrefs = UserPrefs.create(context.applicationContext)
    val updatePrefs: UpdatePrefs = UpdatePrefs.create(context.applicationContext)

    val retrofit = NetworkModule.provideRetrofit(
        baseUrl = "https://billiardtracker.alekseylosev.ru/",
        userPrefs = userPrefs,
    )
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val syncManager: com.example.billiardtracker.data.sync.SyncManager =
        com.example.billiardtracker.data.sync.SyncManager(
            outboxDao = outboxDao,
            shotDao = shotDao,
            networkMonitor = networkMonitor,
            appScope = appScope,
            baseUrl = "https://billiardtracker.alekseylosev.ru/",
            tokenProvider = { userPrefs.getToken() },
        )

    // ----- Repos (order matters: everything below uses infrastructure above) -----
    val authRepository = AuthRepository(apiService, userPrefs)
    val tournamentRepository = TournamentRepository(
        api = apiService,
        tournamentDao = tournamentDao,
        participantDao = participantDao,
        outboxDao = outboxDao,
        syncManager = syncManager,
    )
    val gameRepository: GameRepository = GameRepository(
        api = apiService,
        shotDao = shotDao,
        outboxDao = outboxDao,
        syncManager = syncManager,
        gameDao = gameDao,
    )
    val ruleRepository = RuleRepository(apiService, ruleDao)
    val updaterRepository = UpdaterRepository(apiService)
    val clubRepository = ClubRepository(apiService)
    val donationRepository = DonationRepository(apiService)
    val tokenRepository = TokenRepository(apiService)
    val teamRepository = com.example.billiardtracker.data.repo.TeamRepository(apiService)

    // ----- Domain / UI helpers -----
    val locationProvider = com.example.billiardtracker.data.location.LocationProvider(context.applicationContext)
    val detectClubUseCase = com.example.billiardtracker.domain.usecase.DetectClubUseCase(locationProvider, clubRepository)
    val sseClient = SseClient(
        baseUrl = "https://billiardtracker.alekseylosev.ru/",
        prefs = userPrefs,
    )

    val newTournamentState = com.example.billiardtracker.ui.nav.NewTournamentState()

    val teamState: com.example.billiardtracker.ui.nav.TeamState =
        com.example.billiardtracker.ui.nav.TeamState(userPrefs, teamRepository, appScope)

    val tokenSelfHealMutex: Mutex = Mutex()
}
