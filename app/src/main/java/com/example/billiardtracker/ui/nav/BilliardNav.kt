package com.example.billiardtracker.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.billiardtracker.di.AppContainer
import com.example.billiardtracker.ui.screens.home.HomeScreen
import com.example.billiardtracker.ui.screens.home.HomeViewModel

sealed class Route(val path: String) {
    object Home : Route("home")
    object NewTournament : Route("new-tournament") // Task 3.5+ will implement
    data class Tournament(val id: Long) : Route("tournament/{id}") {
        companion object {
            const val PATH = "tournament/{id}"
            fun build(id: Long) = "tournament/$id"
        }
    }
    object Rules : Route("rules") // Task 3.9
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
            )
        }
        composable(Route.NewTournament.path) {
            // TODO Task 3.5: PickParticipantsScreen
            androidx.compose.material3.Text("New Tournament (Task 3.5)")
        }
        composable(Route.Tournament.PATH) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            // TODO Task 3.7: TournamentScreen
            androidx.compose.material3.Text("Tournament $id (Task 3.7)")
        }
    }
}
