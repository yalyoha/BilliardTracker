package com.example.billiardtracker.ui.screens.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.data.remote.dto.UpdateClubBody
import com.example.billiardtracker.data.repo.ClubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClubsAdminUiState(
    val loading: Boolean = false,
    val clubs: List<ClubDto> = emptyList(),
    val error: String? = null,
)

class ClubsAdminViewModel(private val repo: ClubRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ClubsAdminUiState(loading = true))
    val ui: StateFlow<ClubsAdminUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.listAll().fold(
                onSuccess = { _ui.value = ClubsAdminUiState(loading = false, clubs = it) },
                onFailure = { _ui.value = _ui.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun rename(id: Long, name: String, address: String?) {
        viewModelScope.launch {
            repo.update(id, UpdateClubBody(name = name, address = address)).fold(
                onSuccess = { updated ->
                    _ui.value = _ui.value.copy(
                        clubs = _ui.value.clubs.map { if (it.id == id) updated else it },
                    )
                },
                onFailure = { _ui.value = _ui.value.copy(error = it.message) },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id).fold(
                onSuccess = {
                    _ui.value = _ui.value.copy(
                        clubs = _ui.value.clubs.filterNot { it.id == id },
                    )
                },
                onFailure = { _ui.value = _ui.value.copy(error = it.message) },
            )
        }
    }
}
