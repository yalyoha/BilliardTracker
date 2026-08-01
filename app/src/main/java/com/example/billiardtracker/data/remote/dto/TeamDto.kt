package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TeamDto(
    val id: Long,
    val masterTokenId: Long,
    val name: String,
    val createdAt: Long,
    val members: List<TeamMemberDto> = emptyList(),
)

@Serializable
data class TeamMemberDto(
    val id: Long,
    val teamId: Long,
    val displayName: String,
    val phone: String? = null,
    val addedAt: Long,
)

@Serializable
data class TeamsListDto(val teams: List<TeamDto>)

@Serializable
data class CreateTeamBody(val name: String)

@Serializable
data class RenameTeamBody(val name: String)

@Serializable
data class AddTeamMemberBody(
    val displayName: String,
    val phone: String? = null,
)
