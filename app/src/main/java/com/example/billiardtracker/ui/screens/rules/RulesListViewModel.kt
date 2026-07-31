package com.example.billiardtracker.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.RuleEntity
import com.example.billiardtracker.data.repo.RuleRepository
import com.example.billiardtracker.domain.rules.GameType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesListViewModel(private val repo: RuleRepository) : ViewModel() {
    val rules: StateFlow<List<RuleEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), fallbackList())

    init {
        viewModelScope.launch { repo.refreshList() }
    }

    /**
     * Fallback list — even if network fails and the DB is empty, we still show
     * all 14 [GameType] entries so navigation into a detail works offline.
     */
    private fun fallbackList(): List<RuleEntity> = GameType.entries.map {
        RuleEntity(
            slug = it.ruleFileSlug,
            displayName = it.displayName,
            markdown = "",
            cachedAt = 0L,
        )
    }
}
