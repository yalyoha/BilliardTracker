package com.example.billiardtracker.ui.screens.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.util.digitsToE164
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamPlayer(val name: String, val phone: String?)

data class TeamUiState(
    val players: List<TeamPlayer> = emptyList(),
    val nameDraft: String = "",
    val phoneDraft: String = "",
    val needOnboarding: Boolean = false,
)

class TeamViewModel(private val prefs: UserPrefs) : ViewModel() {
    private val _ui = MutableStateFlow(TeamUiState())
    val ui: StateFlow<TeamUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val phone = prefs.getPhone()
            val name = prefs.getName()
            _ui.value = _ui.value.copy(
                needOnboarding = phone.isNullOrBlank() || name.isNullOrBlank(),
            )
        }
    }

    fun setNameDraft(v: String) { _ui.value = _ui.value.copy(nameDraft = v) }
    fun setPhoneDraft(v: String) { _ui.value = _ui.value.copy(phoneDraft = v) }

    fun addPlayer() {
        val name = _ui.value.nameDraft.trim()
        if (name.isBlank()) return
        val phone = _ui.value.phoneDraft.trim().ifBlank { null }
        _ui.value = _ui.value.copy(
            players = _ui.value.players + TeamPlayer(name = name, phone = phone),
            nameDraft = "",
            phoneDraft = "",
        )
    }

    fun removePlayer(index: Int) {
        _ui.value = _ui.value.copy(
            players = _ui.value.players.filterIndexed { i, _ -> i != index },
        )
    }

    /** phoneDigits — только цифры (11 max, из PhoneMaskInput). */
    fun completeOnboarding(phoneDigits: String, name: String) {
        viewModelScope.launch {
            prefs.setLocalProfile(
                digitsToE164(phoneDigits),
                name.trim().takeIf { it.isNotBlank() },
            )
            _ui.value = _ui.value.copy(needOnboarding = false)
        }
    }
}
