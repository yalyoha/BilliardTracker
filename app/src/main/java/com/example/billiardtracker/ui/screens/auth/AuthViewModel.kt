package com.example.billiardtracker.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val phone: String = "+7",
    val code: String = "",
    val codeRequested: Boolean = false,
    val debugCode: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun setPhone(v: String) {
        _ui.value = _ui.value.copy(phone = v, error = null)
    }

    fun setCode(v: String) {
        _ui.value = _ui.value.copy(code = v, error = null)
    }

    fun requestCode() {
        val phone = _ui.value.phone
        if (!phone.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
            _ui.value = _ui.value.copy(error = "Введите номер в формате +7…")
            return
        }
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            repo.requestCode(phone).fold(
                onSuccess = { debugCode ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        codeRequested = true,
                        debugCode = debugCode,
                    )
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Ошибка",
                    )
                },
            )
        }
    }

    fun verify() {
        val phone = _ui.value.phone
        val code = _ui.value.code
        if (!code.matches(Regex("^\\d{4}$"))) {
            _ui.value = _ui.value.copy(error = "Код — 4 цифры")
            return
        }
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            repo.verify(phone, code).fold(
                onSuccess = {
                    _ui.value = _ui.value.copy(loading = false, loggedIn = true)
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Ошибка",
                    )
                },
            )
        }
    }
}
