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

    /** true → local prefs have no JWT — onboarding gate handles this at app start. */
    val needsCloudLogin: StateFlow<Boolean> = userPrefs.tokenFlow
        .map { it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val activeTokenId: StateFlow<Long?> = userPrefs.activeTokenIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setActiveToken(id: Long) {
        viewModelScope.launch { userPrefs.setActiveTokenId(id) }
    }

    private val _tokens = MutableStateFlow(TokensUiState())
    val tokens: StateFlow<TokensUiState> = _tokens.asStateFlow()

    init {
        // Refresh runs on every VM creation. Reliable across bottom-nav tab
        // switches (saveState/restoreState in NavHost was preventing the
        // previous LaunchedEffect(Unit) approach from re-firing).
        refreshTokens()
    }

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
                    if (list.isEmpty()) {
                        // Идемпотентная ручка на сервере — SELECT+INSERT в одной
                        // транзакции. Даже если два VM запустят это одновременно,
                        // второй увидит уже вставленный первым и вернёт его.
                        tokenRepo.ensureDefault().fold(
                            onSuccess = { token ->
                                _tokens.value = TokensUiState(loading = false, tokens = listOf(token))
                                userPrefs.setActiveTokenId(token.id)
                            },
                            onFailure = { e ->
                                _tokens.value = _tokens.value.copy(
                                    loading = false,
                                    error = e.message ?: "Не удалось создать путь",
                                )
                            },
                        )
                    } else {
                        _tokens.value = TokensUiState(loading = false, tokens = list)
                        val stored = userPrefs.getActiveTokenId()
                        if (stored == null || list.none { it.id == stored }) {
                            userPrefs.setActiveTokenId(list.first().id)
                        }
                    }
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
                onSuccess = {
                    // Re-fetch from server rather than appending locally: keeps
                    // ordering + tournamentCount consistent, and avoids the
                    // list-desync users hit when local optimistic state raced
                    // with a background refresh.
                    reloadTokens()
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

    private suspend fun reloadTokens() {
        tokenRepo.list().fold(
            onSuccess = { list ->
                _tokens.value = TokensUiState(loading = false, tokens = list)
                val stored = userPrefs.getActiveTokenId()
                if ((stored == null || list.none { it.id == stored }) && list.isNotEmpty()) {
                    userPrefs.setActiveTokenId(list.first().id)
                }
            },
            onFailure = { e ->
                _tokens.value = _tokens.value.copy(
                    loading = false,
                    error = e.message ?: "Не удалось загрузить пути",
                )
            },
        )
    }

    fun renameToken(id: Long, name: String?) {
        viewModelScope.launch {
            tokenRepo.rename(id, name).fold(
                onSuccess = { updated ->
                    val list = _tokens.value.tokens.map {
                        if (it.id == id) it.copy(name = updated.name) else it
                    }
                    _tokens.value = _tokens.value.copy(tokens = list, error = null)
                },
                onFailure = { e ->
                    _tokens.value = _tokens.value.copy(
                        error = e.message ?: "Не удалось переименовать",
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
