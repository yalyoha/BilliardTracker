package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.RequestCodeResponse
import com.example.billiardtracker.data.remote.dto.VerifyBody
import com.example.billiardtracker.data.remote.dto.VerifyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/request-code")
    suspend fun requestCode(@Body body: RequestCodeBody): Response<RequestCodeResponse>

    @POST("api/auth/verify")
    suspend fun verify(@Body body: VerifyBody): Response<VerifyResponse>
}
