package com.example.billiardtracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.ui.nav.TeamState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: TournamentRepository,
    private val userPrefs: UserPrefs,
    private val teamState: TeamState,
) : ViewModel() {
    val activeTournaments: StateFlow<List<TournamentEntity>> = repo.observeAll()
        .map { list -> list.filter { it.status == "active" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val enabledGameSlugs: StateFlow<Set<String>> = userPrefs.enabledGameSlugsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPrefs.ALL_SLUGS)

    val hasReadyTeam: StateFlow<Boolean> = teamState.teams
        .map { teams ->
            val activeId = teamState.activeTeamId.value
            val active = teams.firstOrNull { it.id == activeId } ?: teams.firstOrNull()
            active != null && active.players.isNotEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    init {
        viewModelScope.launch {
            userPrefs.activeTokenIdFlow.collect { tokenId ->
                repo.refreshMine(tokenId)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val tokenId = userPrefs.getActiveTokenId()
            repo.refreshMine(tokenId)
        }
    }
}
