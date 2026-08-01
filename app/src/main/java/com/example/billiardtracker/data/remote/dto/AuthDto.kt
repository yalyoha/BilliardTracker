package com.example.billiardtracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RequestCodeBody(val phone: String)

@Serializable
data class RequestCodeResponse(val ok: Boolean = true, val debugCode: String? = null)

@Serializable
data class VerifyBody(val phone: String, val code: String)

@Serializable
data class VerifyResponse(val token: String, val userId: Long)

@Serializable
data class RegisterBody(val phone: String, val name: String)

@Serializable
data class ErrorResponse(val error: String, val retryAfterMs: Long? = null)
