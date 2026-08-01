package com.example.billiardtracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.repo.TournamentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: TournamentRepository,
    private val userPrefs: UserPrefs,
) : ViewModel() {
    val tournaments: StateFlow<List<TournamentEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // React to active-token changes — swap the visible list to just the
        // tournaments bound to that "путь мастера".
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
