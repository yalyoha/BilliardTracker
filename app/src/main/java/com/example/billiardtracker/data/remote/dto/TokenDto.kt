package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TokenDto(
    val id: Long,
    val token: String,
    val name: String? = null,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val tournamentCount: Int = 0,
)

@Serializable
data class TokensListDto(val tokens: List<TokenDto>)

@Serializable
data class CreateTokenBody(val name: String? = null)

@Serializable
data class RotateTokenResponse(val token: String)
