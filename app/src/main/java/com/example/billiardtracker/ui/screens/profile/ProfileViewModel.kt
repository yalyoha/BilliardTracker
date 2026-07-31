package com.example.billiardtracker.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.util.digitsToE164
import com.example.billiardtracker.util.e164ToDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * phoneDigits — только цифры (11 max, начинается с 7). Формат E.164 склеивается
 * при save. Пустые данные = editing автоматически true (нечего показывать view-mode).
 */
data class ProfileUiState(
    val phoneDigits: String = "",
    val name: String = "",
    val saved: Boolean = false,
    val editing: Boolean = true,
    val loaded: Boolean = false,
)

class ProfileViewModel(private val prefs: UserPrefs) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val digits = e164ToDigits(prefs.getPhone())
            val name = prefs.getName() ?: ""
            _ui.value = _ui.value.copy(
                phoneDigits = digits,
                name = name,
                editing = digits.isBlank() && name.isBlank(),
                loaded = true,
            )
        }
    }

    fun setPhoneDigits(v: String) { _ui.value = _ui.value.copy(phoneDigits = v, saved = false) }
    fun setName(v: String) { _ui.value = _ui.value.copy(name = v, saved = false) }

    fun startEdit() { _ui.value = _ui.value.copy(editing = true, saved = false) }

    fun save() {
        val e164 = digitsToE164(_ui.value.phoneDigits)
        val name = _ui.value.name.trim()
        viewModelScope.launch {
            prefs.setLocalProfile(e164, name.takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(saved = true, editing = false)
        }
    }
}
