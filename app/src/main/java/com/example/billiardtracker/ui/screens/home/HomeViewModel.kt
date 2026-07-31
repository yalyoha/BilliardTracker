package com.example.billiardtracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.repo.TournamentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: TournamentRepository) : ViewModel() {
    val tournaments: StateFlow<List<TournamentEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repo.refreshMine() }
    }
}
