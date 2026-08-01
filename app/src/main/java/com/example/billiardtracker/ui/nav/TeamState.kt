package com.example.billiardtracker.ui.nav

import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import kotlinx.coroutines.flow.MutableStateFlow

data class TeamMember(val displayName: String, val phone: String?)

data class Team(
    val id: Long,
    val name: String,
    val players: List<TeamMember> = emptyList(),
)

/**
 * Named team presets. Each team is a saved roster of players you can quickly
 * apply to a new tournament. Lives in memory only — persistence is out of
 * scope for the current iteration. TODO: DataStore-backed if the feature
 * proves useful.
 */
class TeamState {
    val teams = MutableStateFlow<List<Team>>(emptyList())
    val activeTeamId = MutableStateFlow<Long?>(null)

    private var nextId = 1L

    fun addTeam(name: String): Long {
        val id = nextId++
        val newTeam = Team(id = id, name = name.trim().ifBlank { "Команда $id" })
        teams.value = teams.value + newTeam
        if (activeTeamId.value == null) activeTeamId.value = id
        return id
    }

    fun renameTeam(id: Long, name: String) {
        teams.value = teams.value.map {
            if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it
        }
    }

    fun deleteTeam(id: Long) {
        teams.value = teams.value.filterNot { it.id == id }
        if (activeTeamId.value == id) activeTeamId.value = teams.value.firstOrNull()?.id
    }

    fun setActiveTeam(id: Long) { activeTeamId.value = id }

    fun addPlayer(teamId: Long, member: TeamMember) {
        teams.value = teams.value.map {
            if (it.id == teamId) it.copy(players = it.players + member) else it
        }
    }

    fun removePlayer(teamId: Long, index: Int) {
        teams.value = teams.value.map {
            if (it.id == teamId) it.copy(players = it.players.filterIndexed { i, _ -> i != index }) else it
        }
    }

    fun teamById(id: Long?): Team? = teams.value.firstOrNull { it.id == id }

    fun asParticipantBodies(teamId: Long): List<CreateParticipantBody> =
        teamById(teamId)?.players?.map { m ->
            CreateParticipantBody(
                phone = m.phone,
                displayName = m.displayName,
                handicapPoints = 0,
            )
        }.orEmpty()
}
