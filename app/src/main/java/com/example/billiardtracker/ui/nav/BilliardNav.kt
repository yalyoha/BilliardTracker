package com.example.billiardtracker.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.billiardtracker.di.AppContainer
import com.example.billiardtracker.ui.screens.gametype.PickGameTypeScreen
import com.example.billiardtracker.ui.screens.gametype.PickGameTypeViewModel
import com.example.billiardtracker.ui.screens.gametype.StakeSetupScreen
import com.example.billiardtracker.ui.screens.gametype.StakeSetupViewModel
import com.example.billiardtracker.ui.screens.home.HomeScreen
import com.example.billiardtracker.ui.screens.home.HomeViewModel
import com.example.billiardtracker.ui.screens.pick.PickParticipantsScreen
import com.example.billiardtracker.ui.screens.pick.PickParticipantsViewModel
import com.example.billiardtracker.ui.screens.rules.RuleDetailScreen
import com.example.billiardtracker.ui.screens.rules.RuleDetailViewModel
import com.example.billiardtracker.ui.screens.rules.RulesListScreen
import com.example.billiardtracker.ui.screens.rules.RulesListViewModel
import com.example.billiardtracker.ui.screens.settings.SettingsScreen
import com.example.billiardtracker.ui.screens.settings.SettingsViewModel
import com.example.billiardtracker.ui.screens.tournament.TournamentScreen
import com.example.billiardtracker.ui.screens.tournament.TournamentViewModel

sealed class Route(val path: String) {
    object Home : Route("home")
    object NewTournament : Route("new-tournament") // Task 3.5: PickParticipantsScreen
    object PickGameType : Route("pick-game-type")
    object StakeSetup : Route("stake-setup")
    data class Tournament(val id: Long) : Route("tournament/{id}") {
        companion object {
            const val PATH = "tournament/{id}"
            fun build(id: Long) = "tournament/$id"
        }
    }
    object Rules : Route("rules") // Task 3.9
    data class RuleDetail(val slug: String) : Route("rules/{slug}") {
        companion object {
            const val PATH = "rules/{slug}"
            fun build(slug: String) = "rules/$slug"
        }
    }
    object Settings : Route("settings")
}

@Composable
fun BilliardNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Route.Home.path) {
        composable(Route.Home.path) {
            val vm = HomeViewModel(container.tournamentRepository)
            HomeScreen(
                viewModel = vm,
                onNewTournament = { nav.navigate(Route.NewTournament.path) },
                onOpenTournament = { id -> nav.navigate(Route.Tournament.build(id)) },
                onOpenRules = { nav.navigate(Route.Rules.path) },
                onOpenSettings = { nav.navigate(Route.Settings.path) },
            )
        }
        composable(Route.NewTournament.path) {
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
                        popUpTo(Route.Home.path) { inclusive = false }
                    }
                },
            )
        }
        composable(Route.Tournament.PATH) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            val vm = TournamentViewModel(
                tournamentId = id,
                tournamentRepo = container.tournamentRepository,
                gameRepo = container.gameRepository,
                sseClient = container.sseClient,
                userPrefs = container.userPrefs,
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
        composable(Route.RuleDetail.PATH) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            val vm = RuleDetailViewModel(slug, container.ruleRepository)
            RuleDetailScreen(viewModel = vm, onBack = { nav.popBackStack() })
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
    }
}
