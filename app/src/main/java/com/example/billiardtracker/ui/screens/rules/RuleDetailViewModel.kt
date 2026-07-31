package com.example.billiardtracker.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.repo.RuleRepository
import com.example.billiardtracker.domain.rules.GameType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleDetailState(
    val slug: String = "",
    val displayName: String = "",
    val markdown: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class RuleDetailViewModel(
    private val slug: String,
    private val repo: RuleRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RuleDetailState(slug = slug))
    val ui: StateFlow<RuleDetailState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            // 1) Show cached row immediately (if any) — offline-first UX.
            val cached = repo.getBySlug(slug)
            if (cached != null) {
                _ui.value = _ui.value.copy(
                    displayName = cached.displayName,
                    markdown = cached.markdown,
                    loading = cached.markdown.isBlank(),
                )
            } else {
                _ui.value = _ui.value.copy(
                    displayName = fallback(slug),
                    loading = true,
                )
            }
            // 2) Refresh from network in background.
            repo.refreshMarkdown(slug).fold(
                onSuccess = { md ->
                    _ui.value = _ui.value.copy(markdown = md, loading = false)
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = if (cached == null || cached.markdown.isBlank()) e.message else null,
                    )
                },
            )
        }
    }

    private fun fallback(slug: String): String =
        GameType.entries.firstOrNull { it.ruleFileSlug == slug }?.displayName ?: slug
}
