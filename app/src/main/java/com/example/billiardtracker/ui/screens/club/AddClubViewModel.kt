package com.example.billiardtracker.ui.screens.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.location.LocationProvider
import com.example.billiardtracker.data.remote.dto.CreateClubBody
import com.example.billiardtracker.data.repo.ClubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddClubUiState(
    val name: String = "",
    val address: String = "",
    val city: String = "Санкт-Петербург",
    val lat: String = "",
    val lon: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val createdId: Long? = null,
)

class AddClubViewModel(
    private val clubRepo: ClubRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val _ui = MutableStateFlow(AddClubUiState())
    val ui: StateFlow<AddClubUiState> = _ui.asStateFlow()

    init { autofillLocation() }

    fun setName(v: String) { _ui.value = _ui.value.copy(name = v, error = null) }
    fun setAddress(v: String) { _ui.value = _ui.value.copy(address = v) }
    fun setCity(v: String) { _ui.value = _ui.value.copy(city = v) }
    fun setLat(v: String) { _ui.value = _ui.value.copy(lat = v) }
    fun setLon(v: String) { _ui.value = _ui.value.copy(lon = v) }

    fun autofillLocation() {
        viewModelScope.launch {
            locationProvider.getCurrentLocation().onSuccess { p ->
                if (p != null) {
                    _ui.value = _ui.value.copy(
                        lat = "%.6f".format(p.lat),
                        lon = "%.6f".format(p.lon),
                    )
                }
            }
        }
    }

    fun submit() {
        val name = _ui.value.name.trim()
        val lat = _ui.value.lat.toDoubleOrNull()
        val lon = _ui.value.lon.toDoubleOrNull()
        if (name.isBlank()) {
            _ui.value = _ui.value.copy(error = "Введите название")
            return
        }
        if (lat == null || lon == null) {
            _ui.value = _ui.value.copy(error = "Координаты обязательны")
            return
        }
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            clubRepo.create(
                CreateClubBody(
                    name = name,
                    address = _ui.value.address.trim().takeIf { it.isNotBlank() },
                    lat = lat,
                    lon = lon,
                    city = _ui.value.city.trim().takeIf { it.isNotBlank() },
                ),
            ).fold(
                onSuccess = { c -> _ui.value = _ui.value.copy(loading = false, createdId = c.id) },
                onFailure = { e -> _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Ошибка") },
            )
        }
    }
}
