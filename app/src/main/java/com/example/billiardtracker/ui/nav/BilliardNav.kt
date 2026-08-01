package com.example.billiardtracker.ui.nav

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.ui.platform.LocalContext
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
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab(Route.Game.path, "Игра", Icons.Filled.EmojiEvents),
    NavTab(Route.Team.path, "Команды", Icons.Filled.Groups),
    NavTab(Route.Profile.path, "Профиль", Icons.Filled.Person),
    NavTab(Route.Rules.path, "Правила", Icons.Filled.Article),
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
    val ctx = LocalContext.current

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
                        tab.route == Route.Rules.path && currentRoute == Route.RuleDetail.path -> true
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
        NavHost(
            navController = nav,
            startDestination = Route.Game.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Game.path) {
                val vm = HomeViewModel(container.tournamentRepository, container.userPrefs)
                HomeScreen(
                    viewModel = vm,
                    onNewTournament = {
                        val teamState = container.teamState
                        val activeId = teamState.activeTeamId.value
                        val activeTeam = teamState.teamById(activeId)
                        if (activeTeam == null || activeTeam.players.isEmpty()) {
                            Toast.makeText(
                                ctx,
                                if (activeTeam == null) "Сначала создай команду во вкладке Команды"
                                else "Добавь в команду игроков",
                                Toast.LENGTH_SHORT,
                            ).show()
                            nav.navigate(Route.Team.path) {
                                popUpTo(Route.Game.path) { saveState = true; inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            container.newTournamentState.participants.value =
                                teamState.asParticipantBodies(activeTeam.id)
                            nav.navigate(Route.PickGameType.path)
                        }
                    },
                    onOpenTournament = { id -> nav.navigate(Route.Tournament.build(id)) },
                    onOpenPayout = { id -> nav.navigate(Route.Payout.build(id)) },
                    onOpenSettings = { nav.navigate(Route.Settings.path) },
                    onAddClub = { nav.navigate(Route.AddClub.path) },
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
                    onBack = null,
                    onOpen = { slug -> nav.navigate(Route.RuleDetail.build(slug)) },
                )
            }
            composable(Route.Stats.path) {
                val vm = StatsViewModel(container.apiService, container.userPrefs)
                StatsScreen(viewModel = vm)
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
                SettingsScreen(vm, onBack = { nav.popBackStack() })
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
                val vm = StakeSetupViewModel(
                    container.newTournamentState,
                    container.tournamentRepository,
                    container.userPrefs,
                )
                StakeSetupScreen(
                    viewModel = vm,
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
        }
    }
}
