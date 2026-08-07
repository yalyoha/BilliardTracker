package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DevLogEvent(
    val ts: Long,
    val user_id: Long? = null,
    val device: String? = null,
    val session: String? = null,
    val kind: String,
    val screen: String? = null,
    val action: String,
    val payload: JsonElement? = null,
    val ok: Boolean? = null,
    val http_code: Int? = null,
    val err: String? = null,
)

@Serializable
data class DevLogBatchBody(
    val events: List<DevLogEvent>,
)
