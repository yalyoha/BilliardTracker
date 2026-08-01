package com.example.billiardtracker.ui.screens.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.contacts.Contact
import com.example.billiardtracker.data.contacts.ContactsReader
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.ui.nav.Team
import com.example.billiardtracker.ui.nav.TeamState
import com.example.billiardtracker.util.digitsToE164
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TeamsUiState(
    val teams: List<Team> = emptyList(),
    val activeTeamId: Long? = null,
    val expandedTeamId: Long? = null,
    val nameDraft: String = "",
    val phoneDraft: String = "",
    val allContacts: List<Contact> = emptyList(),
    val contactsRequested: Boolean = false,
    val contactsGranted: Boolean = false,
    val error: String? = null,
) {
    val filteredContacts: List<Contact>
        get() {
            val q = nameDraft.trim()
            if (q.length < 1 || allContacts.isEmpty()) return emptyList()
            val lower = q.lowercase()
            return allContacts.asSequence()
                .filter { it.name.lowercase().contains(lower) }
                .take(5)
                .toList()
        }
}

private data class Draft(
    val expandedTeamId: Long? = null,
    val nameDraft: String = "",
    val phoneDraft: String = "",
    val allContacts: List<Contact> = emptyList(),
    val contactsRequested: Boolean = false,
    val contactsGranted: Boolean = false,
    val error: String? = null,
)

class TeamViewModel(
    private val prefs: UserPrefs,
    private val teamState: TeamState,
) : ViewModel() {
    private val _draft = MutableStateFlow(Draft())

    val ui: StateFlow<TeamsUiState> = combine(
        teamState.teams,
        teamState.activeTeamId,
        _draft,
    ) { teams, active, draft ->
        TeamsUiState(
            teams = teams,
            activeTeamId = active,
            expandedTeamId = draft.expandedTeamId ?: active ?: teams.firstOrNull()?.id,
            nameDraft = draft.nameDraft,
            phoneDraft = draft.phoneDraft,
            allContacts = draft.allContacts,
            contactsRequested = draft.contactsRequested,
            contactsGranted = draft.contactsGranted,
            error = draft.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TeamsUiState())

    init {
        // Force refresh на входе — данные могли устареть, а server — истина.
        viewModelScope.launch { teamState.refresh() }
    }

    fun setNameDraft(v: String) { _draft.value = _draft.value.copy(nameDraft = v) }
    fun setPhoneDraft(v: String) { _draft.value = _draft.value.copy(phoneDraft = v) }
    fun expandTeam(id: Long) { _draft.value = _draft.value.copy(expandedTeamId = id) }
    fun setActive(id: Long) { teamState.setActiveTeam(id) }
    fun clearError() { _draft.value = _draft.value.copy(error = null) }

    fun createTeam(name: String) {
        viewModelScope.launch {
            teamState.addTeam(name).fold(
                onSuccess = { team -> _draft.value = _draft.value.copy(expandedTeamId = team.id) },
                onFailure = { e -> _draft.value = _draft.value.copy(error = e.message ?: "Не удалось создать команду") },
            )
        }
    }

    fun renameTeam(id: Long, name: String) {
        viewModelScope.launch {
            teamState.renameTeam(id, name).onFailure { e ->
                _draft.value = _draft.value.copy(error = e.message ?: "Не удалось переименовать")
            }
        }
    }

    fun deleteTeam(id: Long) {
        viewModelScope.launch {
            teamState.deleteTeam(id).onFailure { e ->
                _draft.value = _draft.value.copy(error = e.message ?: "Не удалось удалить")
            }
        }
    }

    fun onContactsPermissionGranted(reader: ContactsReader) {
        _draft.value = _draft.value.copy(contactsGranted = true, contactsRequested = true)
        viewModelScope.launch(Dispatchers.IO) {
            val all = reader.readAll()
            _draft.value = _draft.value.copy(allContacts = all)
        }
    }

    fun onContactsPermissionDenied() {
        _draft.value = _draft.value.copy(contactsRequested = true, contactsGranted = false)
    }

    fun pickContact(c: Contact) {
        _draft.value = _draft.value.copy(
            nameDraft = c.name,
            phoneDraft = c.phone.filter { it.isDigit() },
        )
    }

    fun addPlayer(teamId: Long) {
        val name = _draft.value.nameDraft.trim()
        if (name.isBlank()) return
        val phoneDigits = _draft.value.phoneDraft.trim().ifBlank { null }
        val e164 = phoneDigits?.let { digitsToE164(it) }
        viewModelScope.launch {
            teamState.addPlayer(teamId, name, e164 ?: phoneDigits).fold(
                onSuccess = { _draft.value = _draft.value.copy(nameDraft = "", phoneDraft = "") },
                onFailure = { e -> _draft.value = _draft.value.copy(error = e.message ?: "Не удалось добавить игрока") },
            )
        }
    }

    fun removePlayer(teamId: Long, index: Int) {
        viewModelScope.launch {
            teamState.removePlayerAt(teamId, index).onFailure { e ->
                _draft.value = _draft.value.copy(error = e.message ?: "Не удалось удалить игрока")
            }
        }
    }
}
