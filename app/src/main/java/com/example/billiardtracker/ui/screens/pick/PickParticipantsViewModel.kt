package com.example.billiardtracker.ui.screens.pick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.contacts.Contact
import com.example.billiardtracker.data.contacts.ContactsReader
import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import com.example.billiardtracker.ui.nav.NewTournamentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PickUiState(
    val contactsLoaded: Boolean = false,
    val permissionDenied: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val guests: List<CreateParticipantBody> = emptyList(),
    val guestNameDraft: String = "",
    val guestPhoneDraft: String = "",
)

class PickParticipantsViewModel(
    private val newTournamentState: NewTournamentState,
) : ViewModel() {
    private val _ui = MutableStateFlow(PickUiState())
    val ui: StateFlow<PickUiState> = _ui.asStateFlow()

    fun onPermissionGranted(reader: ContactsReader) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = reader.readAll()
            _ui.value = _ui.value.copy(contactsLoaded = true, permissionDenied = false, contacts = list)
        }
    }

    fun onPermissionDenied() {
        _ui.value = _ui.value.copy(contactsLoaded = true, permissionDenied = true)
    }

    fun toggle(idx: Int) {
        val s = _ui.value.selected.toMutableSet()
        if (!s.add(idx)) s.remove(idx)
        _ui.value = _ui.value.copy(selected = s)
    }

    fun setGuestName(v: String) { _ui.value = _ui.value.copy(guestNameDraft = v) }
    fun setGuestPhone(v: String) { _ui.value = _ui.value.copy(guestPhoneDraft = v) }
    fun addGuest() {
        val name = _ui.value.guestNameDraft.trim()
        if (name.isBlank()) return
        val phone = _ui.value.guestPhoneDraft.trim().ifBlank { null }
        val guests = _ui.value.guests + CreateParticipantBody(
            phone = phone, displayName = name, handicapPoints = 0,
        )
        _ui.value = _ui.value.copy(guests = guests, guestNameDraft = "", guestPhoneDraft = "")
    }

    fun commit(onNext: () -> Unit) {
        val fromContacts = _ui.value.selected.map { i ->
            val c = _ui.value.contacts[i]
            CreateParticipantBody(phone = c.phone, displayName = c.name)
        }
        val all = fromContacts + _ui.value.guests
        newTournamentState.participants.value = all
        onNext()
    }
}
