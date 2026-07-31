package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VersionDto(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val releasedAt: String? = null,
    val changelog: String = "",
)
