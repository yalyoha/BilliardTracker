package com.example.billiardtracker.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.StatsMeDto
import com.example.billiardtracker.data.repo.TournamentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: StatsMeDto? = null,
)

class StatsViewModel(
    private val api: ApiService,
    private val userPrefs: UserPrefs,
    private val tournamentRepo: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    val finishedTournaments: StateFlow<List<TournamentEntity>> = tournamentRepo.observeAll()
        .map { list -> list.filter { it.status != "active" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // Auto-refresh on token switch so W/L reflects only the active path.
        viewModelScope.launch {
            userPrefs.activeTokenIdFlow.collect { tokenId ->
                fetch(tokenId)
            }
        }
    }

    private suspend fun fetch(tokenId: Long?) {
        _ui.value = _ui.value.copy(loading = true, error = null)
        try {
            val res = api.getMyStats(tokenId)
            if (res.isSuccessful) {
                _ui.value = StatsUiState(loading = false, stats = res.body())
            } else {
                _ui.value = _ui.value.copy(loading = false, error = "HTTP ${res.code()}")
            }
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Ошибка")
        }
    }
}
