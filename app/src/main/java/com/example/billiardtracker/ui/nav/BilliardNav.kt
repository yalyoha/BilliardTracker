package com.example.billiardtracker.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.billiardtracker.ui.screens.pick.PickParticipantsScreen
import com.example.billiardtracker.ui.screens.pick.PickParticipantsViewModel
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
import com.example.billiardtracker.ui.screens.tournament.TournamentScreen
import com.example.billiardtracker.ui.screens.tournament.TournamentViewModel

sealed class Route(val path: String) {
    object Game : Route("game")            // bottom-nav tab: список турниров
    object Team : Route("team")            // bottom-nav tab: набор команды
    object Profile : Route("profile")      // bottom-nav tab: имя+телефон
    object Stats : Route("stats")          // bottom-nav tab: статистика
    object Settings : Route("settings")    // bottom-nav tab: настройки + updater

    // Sub-routes (не в bottom-nav):
    object NewTournamentParticipants : Route("new-tournament-participants")
    object PickGameType : Route("pick-game-type")
    object StakeSetup : Route("stake-setup")
    object Tournament : Route("tournament/{id}") {
        fun build(id: Long) = "tournament/$id"
    }
    object Rules : Route("rules")
    object RuleDetail : Route("rules/{slug}") {
        fun build(slug: String) = "rules/$slug"
    }
    object AddClub : Route("add-club")
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab(Route.Game.path, "Игра", Icons.Filled.EmojiEvents),
    NavTab(Route.Team.path, "Команда", Icons.Filled.Groups),
    NavTab(Route.Profile.path, "Профиль", Icons.Filled.Person),
    NavTab(Route.Stats.path, "Статистика", Icons.Filled.QueryStats),
    NavTab(Route.Settings.path, "Настройки", Icons.Filled.Settings),
)

@Composable
fun BilliardNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TABS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Route.Game.path) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
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
                val vm = HomeViewModel(container.tournamentRepository)
                HomeScreen(
                    viewModel = vm,
                    onNewTournament = { nav.navigate(Route.NewTournamentParticipants.path) },
                    onOpenTournament = { id -> nav.navigate(Route.Tournament.build(id)) },
                    onOpenRules = { nav.navigate(Route.Rules.path) },
                    onOpenSettings = { nav.navigate(Route.Settings.path) },
                    onAddClub = { nav.navigate(Route.AddClub.path) },
                )
            }
            composable(Route.Team.path) {
                val vm = TeamViewModel(container.userPrefs)
                TeamScreen(viewModel = vm)
            }
            composable(Route.Profile.path) {
                val vm = ProfileViewModel(container.userPrefs)
                ProfileScreen(viewModel = vm)
            }
            composable(Route.Stats.path) {
                val vm = StatsViewModel(container.tournamentRepository)
                StatsScreen(viewModel = vm)
            }
            composable(Route.Settings.path) {
                val vm = SettingsViewModel(
                    updatePrefs = container.updatePrefs,
                    updater = container.updaterRepository,
                    authRepo = container.authRepository,
                    currentVersionCode = com.example.billiardtracker.BuildConfig.VERSION_CODE,
                )
                SettingsScreen(vm, onBack = { nav.popBackStack() })
            }

            // sub-routes
            composable(Route.NewTournamentParticipants.path) {
                val vm = PickParticipantsViewModel(container.newTournamentState)
                PickParticipantsScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onNext = { nav.navigate(Route.PickGameType.path) },
                )
            }
            composable(Route.PickGameType.path) {
                val vm = PickGameTypeViewModel(container.newTournamentState)
                PickGameTypeScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onOpenRules = { slug -> nav.navigate(Route.RuleDetail.build(slug)) },
                    onNext = { nav.navigate(Route.StakeSetup.path) },
                )
            }
            composable(Route.StakeSetup.path) {
                val vm = StakeSetupViewModel(container.newTournamentState, container.tournamentRepository)
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
                TournamentScreen(viewModel = vm, onBack = { nav.popBackStack() })
            }
            composable(Route.Rules.path) {
                val vm = RulesListViewModel(container.ruleRepository)
                RulesListScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { slug -> nav.navigate(Route.RuleDetail.build(slug)) },
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
