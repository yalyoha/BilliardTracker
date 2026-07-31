package com.example.billiardtracker.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val phone: String = "",
    val name: String = "",
    val saved: Boolean = false,
)

class ProfileViewModel(private val prefs: UserPrefs) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                phone = prefs.getPhone() ?: "",
                name = prefs.getName() ?: "",
            )
        }
    }

    fun setPhone(v: String) { _ui.value = _ui.value.copy(phone = v, saved = false) }
    fun setName(v: String) { _ui.value = _ui.value.copy(name = v, saved = false) }

    fun save() {
        val phone = _ui.value.phone.trim()
        val name = _ui.value.name.trim()
        viewModelScope.launch {
            prefs.setLocalProfile(phone.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(saved = true)
        }
    }
}
