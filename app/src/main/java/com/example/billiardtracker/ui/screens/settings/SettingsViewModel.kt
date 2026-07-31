package com.example.billiardtracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UpdatePrefs
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.dto.TokenDto
import com.example.billiardtracker.data.remote.dto.VersionDto
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.data.repo.TokenRepository
import com.example.billiardtracker.data.repo.UpdaterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoCheck: Boolean = true,
    val latest: VersionDto? = null,
    val checking: Boolean = false,
    val message: String? = null,
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "?",
)

data class TokensUiState(
    val loading: Boolean = false,
    val tokens: List<TokenDto> = emptyList(),
    val error: String? = null,
)

class SettingsViewModel(
    private val updatePrefs: UpdatePrefs,
    private val updater: UpdaterRepository,
    private val authRepo: AuthRepository,
    private val tokenRepo: TokenRepository,
    private val userPrefs: UserPrefs,
    private val currentVersionCode: Int,
    private val currentVersionName: String = "?",
) : ViewModel() {
    private val _ui = MutableStateFlow(
        SettingsUiState(currentVersionCode = currentVersionCode, currentVersionName = currentVersionName)
    )
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    val autoCheck: StateFlow<Boolean> = updatePrefs.autoCheckFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** true → local prefs have no JWT → need to show CloudLoginDialog before token ops. */
    val needsCloudLogin: StateFlow<Boolean> = userPrefs.tokenFlow
        .map { it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _tokens = MutableStateFlow(TokensUiState())
    val tokens: StateFlow<TokensUiState> = _tokens.asStateFlow()

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

    // --- Master-tokens ("Пути мастера") ---

    fun refreshTokens() {
        _tokens.value = _tokens.value.copy(loading = true, error = null)
        viewModelScope.launch {
            tokenRepo.list().fold(
                onSuccess = { list ->
                    _tokens.value = TokensUiState(loading = false, tokens = list)
                },
                onFailure = { e ->
                    _tokens.value = _tokens.value.copy(
                        loading = false,
                        error = e.message ?: "Не удалось загрузить пути",
                    )
                },
            )
        }
    }

    fun createToken(name: String?) {
        _tokens.value = _tokens.value.copy(loading = true, error = null)
        viewModelScope.launch {
            tokenRepo.create(name).fold(
                onSuccess = { created ->
                    _tokens.value = _tokens.value.copy(
                        loading = false,
                        tokens = _tokens.value.tokens + created,
                    )
                },
                onFailure = { e ->
                    _tokens.value = _tokens.value.copy(
                        loading = false,
                        error = e.message ?: "Не удалось создать путь",
                    )
                },
            )
        }
    }

    fun rotateToken(id: Long) {
        viewModelScope.launch {
            tokenRepo.rotate(id).fold(
                onSuccess = { newToken ->
                    val updated = _tokens.value.tokens.map {
                        if (it.id == id) it.copy(token = newToken) else it
                    }
                    _tokens.value = _tokens.value.copy(tokens = updated, error = null)
                },
                onFailure = { e ->
                    _tokens.value = _tokens.value.copy(
                        error = e.message ?: "Не удалось сменить ссылку",
                    )
                },
            )
        }
    }

    fun deleteToken(id: Long) {
        viewModelScope.launch {
            tokenRepo.delete(id).fold(
                onSuccess = {
                    _tokens.value = _tokens.value.copy(
                        tokens = _tokens.value.tokens.filterNot { it.id == id },
                        error = null,
                    )
                },
                onFailure = { e ->
                    _tokens.value = _tokens.value.copy(
                        error = e.message ?: "Не удалось удалить путь",
                    )
                },
            )
        }
    }

    fun clearTokensError() {
        _tokens.value = _tokens.value.copy(error = null)
    }
}
