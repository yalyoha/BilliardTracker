package com.example.billiardtracker.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.local.entity.OutboxOpEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.billiardtracker.di.AppContainer
import com.example.billiardtracker.ui.screens.club.AddClubScreen
import com.example.billiardtracker.ui.screens.club.AddClubViewModel
import com.example.billiardtracker.ui.screens.gametype.PickGameTypeScreen
import com.example.billiardtracker.ui.screens.gametype.PickGameTypeViewModel
import com.example.billiardtracker.ui.screens.gametype.StakeSetupScreen
import com.example.billiardtracker.ui.screens.gametype.StakeSetupViewModel
import com.example.billiardtracker.ui.screens.home.HomeScreen
import com.example.billiardtracker.ui.screens.home.HomeViewModel
import com.example.billiardtracker.ui.screens.onboarding.OnboardingScreen
import com.example.billiardtracker.ui.screens.profile.ProfileScreen
import com.example.billiardtracker.ui.screens.profile.ProfileViewModel
import com.example.billiardtracker.ui.screens.rules.RuleDetailScreen
import com.example.billiardtracker.ui.screens.rules.RuleDetailViewModel
import com.example.billiardtracker.ui.screens.rules.RulesListScreen
import com.example.billiardtracker.ui.screens.rules.RulesListViewModel
import com.example.billiardtracker.ui.screens.settings.SettingsScreen
import com.example.billiardtracker.ui.screens.settings.SettingsViewModel
import com.example.billiardtracker.ui.screens.stats.StatsScreen
import com.example.billiardtracker.ui.screens.stats.StatsViewModel
import com.example.billiardtracker.ui.screens.team.TeamScreen
import com.example.billiardtracker.ui.screens.team.TeamViewModel
import com.example.billiardtracker.ui.screens.tournament.PayoutScreen
import com.example.billiardtracker.ui.screens.tournament.TournamentScreen
import com.example.billiardtracker.ui.screens.tournament.TournamentViewModel

sealed class Route(val path: String) {
    object Game : Route("game")
    object Team : Route("team")
    object Profile : Route("profile")
    object Rules : Route("rules-tab")
    object Stats : Route("stats")
    object Settings : Route("settings")

    // Sub-routes.
    object PickGameType : Route("pick-game-type")
    object StakeSetup : Route("stake-setup")
    object Tournament : Route("tournament/{id}") {
        fun build(id: Long) = "tournament/$id"
    }
    object Payout : Route("tournament/{id}/payout") {
        fun build(id: Long) = "tournament/$id/payout"
    }
    object RuleDetail : Route("rules/{slug}") {
        fun build(slug: String) = "rules/$slug"
    }
    object AddClub : Route("add-club")
    object ClubsAdmin : Route("clubs-admin")
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

// v1.23.0: Составы больше не отдельная вкладка — управление составами
// перенесено внутрь экрана «Создать встречу» (StakeSetupScreen). Route.Team
// пока оставлен как sub-route (Home → Team fallback), но в bottom-nav не входит.
private val TABS = listOf(
    NavTab(Route.Game.path, "Игра", Icons.Filled.EmojiEvents),
    NavTab(Route.Profile.path, "Профиль", Icons.Filled.Person),
    NavTab(Route.Stats.path, "Статистика", Icons.Filled.QueryStats),
    NavTab(Route.Settings.path, "Настройки", Icons.Filled.Settings),
)

@Composable
fun BilliardNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    // Bootstrap: if no JWT locally, block the app with OnboardingScreen until
    // the user registers + creates their first master-token. Reactive collect
    // means logout / clearing prefs also flips us back to onboarding.
    var checked by remember { mutableStateOf(false) }
    var needsOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        container.userPrefs.tokenFlow.collect { t ->
            val hasJwt = !t.isNullOrBlank()
            needsOnboarding = !hasJwt
            checked = true
            // Consume any pending deep-link token (user opened an invite link
            // before onboarding, we stashed it).
            if (hasJwt) {
                val pending = container.userPrefs.getPendingSharedToken()
                if (!pending.isNullOrBlank()) {
                    container.userPrefs.setPendingSharedToken(null)
                    container.tokenRepository.subscribe(pending).onSuccess { adopted ->
                        container.userPrefs.setActiveTokenId(adopted.id)
                    }
                }
            }
        }
    }
    if (!checked) return
    if (needsOnboarding) {
        OnboardingScreen(
            authRepo = container.authRepository,
            onDone = { /* tokenFlow will emit new JWT → recomposition swaps this out */ },
        )
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val gameFlowRoutes = setOf(
        Route.PickGameType.path,
        Route.StakeSetup.path,
        Route.Tournament.path,
        Route.Payout.path,
        Route.AddClub.path,
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEach { tab ->
                    val selected = when {
                        currentRoute == tab.route -> true
                        tab.route == Route.Game.path && currentRoute in gameFlowRoutes -> true
                        tab.route == Route.Settings.path && (
                            currentRoute == Route.ClubsAdmin.path ||
                            currentRoute == Route.Rules.path ||
                            currentRoute == Route.RuleDetail.path
                        ) -> true
                        else -> backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(tab.route) {
                                    popUpTo(Route.Game.path) {
                                        saveState = true
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        alwaysShowLabel = false,
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val pendingSync by container.pendingSyncCount.collectAsStateWithLifecycle(0)
        val pendingOps by container.pendingSyncOps.collectAsStateWithLifecycle(emptyList())
        var showPendingDialog by remember { mutableStateOf(false) }
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Route.Game.path,
                modifier = Modifier.fillMaxSize(),
            ) {
            composable(Route.Game.path) {
                val vm = HomeViewModel(container.tournamentRepository, container.userPrefs)
                HomeScreen(
                    viewModel = vm,
                    onPickGameType = { gt ->
                        // v1.23.0: состав больше не выбирается на Home — только
                        // тип игры. Участники набираются на StakeSetupScreen
                        // (там встроенный team-picker/editor).
                        container.newTournamentState.gameType.value = gt.ruleFileSlug
                        container.newTournamentState.participants.value = emptyList()
                        nav.navigate(Route.StakeSetup.path)
                    },
                    onOpenTournament = { id -> nav.navigate(Route.Tournament.build(id)) },
                )
            }
            composable(Route.Team.path) {
                val vm = TeamViewModel(container.userPrefs, container.teamState)
                TeamScreen(viewModel = vm)
            }
            composable(Route.Profile.path) {
                val vm = ProfileViewModel(container.userPrefs)
                ProfileScreen(viewModel = vm)
            }
            composable(Route.Rules.path) {
                val vm = RulesListViewModel(container.ruleRepository)
                RulesListScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { slug -> nav.navigate(Route.RuleDetail.build(slug)) },
                )
            }
            composable(Route.Stats.path) {
                val vm = StatsViewModel(container.apiService, container.userPrefs, container.tournamentRepository, container.gameDao, container.participantDao)
                StatsScreen(
                    viewModel = vm,
                    onOpenPayout = { id -> nav.navigate(Route.Payout.build(id)) },
                )
            }
            composable(Route.Settings.path) {
                val vm = SettingsViewModel(
                    updatePrefs = container.updatePrefs,
                    updater = container.updaterRepository,
                    authRepo = container.authRepository,
                    tokenRepo = container.tokenRepository,
                    userPrefs = container.userPrefs,
                    currentVersionCode = com.example.billiardtracker.BuildConfig.VERSION_CODE,
                    currentVersionName = com.example.billiardtracker.BuildConfig.VERSION_NAME,
                )
                SettingsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenClubs = { nav.navigate(Route.ClubsAdmin.path) },
                    onOpenRules = { nav.navigate(Route.Rules.path) },
                )
            }

            composable(Route.PickGameType.path) {
                val vm = PickGameTypeViewModel(container.newTournamentState)
                PickGameTypeScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onNext = { nav.navigate(Route.StakeSetup.path) },
                )
            }
            composable(Route.StakeSetup.path) {
                val stakeVm = StakeSetupViewModel(
                    container.newTournamentState,
                    container.tournamentRepository,
                    container.userPrefs,
                    container.detectClubUseCase,
                    container.teamState,
                )
                val teamVm = com.example.billiardtracker.ui.screens.team.TeamViewModel(
                    container.userPrefs,
                    container.teamState,
                )
                StakeSetupScreen(
                    viewModel = stakeVm,
                    teamViewModel = teamVm,
                    onBack = { nav.popBackStack() },
                    onCreated = { id ->
                        nav.navigate(Route.Tournament.build(id)) {
                            popUpTo(Route.Game.path) { inclusive = false }
                        }
                    },
                )
            }
            composable(Route.Tournament.path) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                val vm = TournamentViewModel(
                    tournamentId = id,
                    tournamentRepo = container.tournamentRepository,
                    gameRepo = container.gameRepository,
                    sseClient = container.sseClient,
                    userPrefs = container.userPrefs,
                    donationRepo = container.donationRepository,
                )
                TournamentScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onOpenPayout = {
                        nav.navigate(Route.Payout.build(id)) {
                            popUpTo(Route.Game.path) { inclusive = false }
                        }
                    },
                )
            }
            composable(Route.Payout.path) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                val vm = TournamentViewModel(
                    tournamentId = id,
                    tournamentRepo = container.tournamentRepository,
                    gameRepo = container.gameRepository,
                    sseClient = container.sseClient,
                    userPrefs = container.userPrefs,
                    donationRepo = container.donationRepository,
                )
                PayoutScreen(
                    viewModel = vm,
                    onClose = {
                        nav.navigate(Route.Game.path) {
                            popUpTo(Route.Game.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Route.RuleDetail.path) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
                val vm = RuleDetailViewModel(slug, container.ruleRepository)
                RuleDetailScreen(viewModel = vm, onBack = { nav.popBackStack() })
            }
            composable(Route.AddClub.path) {
                val vm = AddClubViewModel(container.clubRepository, container.locationProvider)
                AddClubScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onCreated = { nav.popBackStack() },
                )
            }
            composable(Route.ClubsAdmin.path) {
                val vm = com.example.billiardtracker.ui.screens.club.ClubsAdminViewModel(container.clubRepository)
                com.example.billiardtracker.ui.screens.club.ClubsAdminScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onAddClub = { nav.navigate(Route.AddClub.path) },
                )
            }
            }
            // Overlay: pending offline-ops counter. Виден только когда > 0.
            if (pendingSync > 0) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(8.dp)
                        .clickable { showPendingDialog = true },
                    shape = androidx.compose.material3.MaterialTheme.shapes.small,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 2.dp,
                ) {
                    androidx.compose.material3.Text(
                        "⏳ $pendingSync",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (showPendingDialog) {
                PendingOpsDialog(
                    ops = pendingOps,
                    onDismiss = { showPendingDialog = false },
                    onRetry = { container.syncManager.kickDrain() },
                )
            }
        }
    }
}

@Composable
private fun PendingOpsDialog(
    ops: List<OutboxOpEntity>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ожидают отправки") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Данные отправятся автоматически при появлении сети.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ops.forEachIndexed { idx, op ->
                    if (idx > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            kindLabel(op.kind),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (op.attempts > 0) {
                            Text(
                                "Попыток: ${op.attempts}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (op.lastError != null) {
                            Text(
                                "Ошибка: ${op.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = {
            TextButton(onClick = { onRetry(); onDismiss() }) { Text("Повторить") }
        },
    )
}

private fun kindLabel(kind: String) = when (kind) {
    "create_tournament" -> "Создание встречи"
    "delete_tournament" -> "Удаление встречи"
    "start_game"        -> "Начало игры"
    "finish_game"       -> "Завершение игры"
    "finish_tournament" -> "Завершение встречи"
    "add_shot"          -> "Добавление удара"
    "delete_shot"       -> "Удаление удара"
    "create_team"       -> "Создание состава"
    "rename_team"       -> "Переименование состава"
    "delete_team"       -> "Удаление состава"
    "add_team_member"   -> "Добавление участника"
    "delete_team_member"-> "Удаление участника"
    else                -> kind
}
