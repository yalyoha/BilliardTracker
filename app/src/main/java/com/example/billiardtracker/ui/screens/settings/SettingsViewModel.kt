package com.example.billiardtracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UpdatePrefs
import com.example.billiardtracker.data.remote.dto.VersionDto
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.data.repo.UpdaterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoCheck: Boolean = true,
    val latest: VersionDto? = null,
    val checking: Boolean = false,
    val message: String? = null,
    val currentVersionCode: Int = 1,
)

class SettingsViewModel(
    private val updatePrefs: UpdatePrefs,
    private val updater: UpdaterRepository,
    private val authRepo: AuthRepository,
    private val currentVersionCode: Int,
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState(currentVersionCode = currentVersionCode))
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    val autoCheck: StateFlow<Boolean> = updatePrefs.autoCheckFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoCheck(v: Boolean) { viewModelScope.launch { updatePrefs.setAutoCheck(v) } }

    fun checkNow() {
        _ui.value = _ui.value.copy(checking = true, message = null)
        viewModelScope.launch {
            updater.fetchLatest().fold(
                onSuccess = { v ->
                    val msg = if (v.versionCode > currentVersionCode)
                        "Доступно обновление v${v.versionName}"
                    else "У вас последняя версия (v${v.versionName})"
                    _ui.value = _ui.value.copy(checking = false, latest = v, message = msg)
                },
                onFailure = {
                    _ui.value = _ui.value.copy(checking = false, message = "Не удалось: ${it.message}")
                },
            )
        }
    }

    fun skip(versionCode: Int) { viewModelScope.launch { updatePrefs.setSkipVersionCode(versionCode) } }
    fun logout() { viewModelScope.launch { authRepo.logout() } }
}
