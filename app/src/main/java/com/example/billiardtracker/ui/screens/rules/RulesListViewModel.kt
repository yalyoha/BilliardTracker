package com.example.billiardtracker.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.entity.RuleEntity
import com.example.billiardtracker.data.repo.RuleRepository
import com.example.billiardtracker.domain.rules.GameType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesListViewModel(private val repo: RuleRepository) : ViewModel() {
    // Order matches [GameType.entries] — same sequence users see in the
    // game-type picker. DAO returns alphabetically which was misleading:
    // "Свободная пирамида" (most common) landed mid-list.
    private val orderBySlug: Map<String, Int> =
        GameType.entries.mapIndexed { idx, gt -> gt.ruleFileSlug to idx }.toMap()

    val rules: StateFlow<List<RuleEntity>> = repo.observeAll()
        .map { list -> list.sortedBy { orderBySlug[it.slug] ?: Int.MAX_VALUE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), fallbackList())

    init {
        viewModelScope.launch { repo.refreshList() }
    }

    private fun fallbackList(): List<RuleEntity> = GameType.entries.map {
        RuleEntity(
            slug = it.ruleFileSlug,
            displayName = it.displayName,
            markdown = "",
            cachedAt = 0L,
        )
    }
}
