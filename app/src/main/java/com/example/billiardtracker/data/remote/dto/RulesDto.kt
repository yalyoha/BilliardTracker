package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RuleItemDto(val slug: String, val displayName: String)

@Serializable
data class RulesListDto(val rules: List<RuleItemDto>)
