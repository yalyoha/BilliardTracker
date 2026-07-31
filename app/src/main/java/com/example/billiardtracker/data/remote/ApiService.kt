package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.RequestCodeResponse
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.remote.dto.TournamentsListDto
import com.example.billiardtracker.data.remote.dto.VerifyBody
import com.example.billiardtracker.data.remote.dto.VerifyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/auth/request-code")
    suspend fun requestCode(@Body body: RequestCodeBody): Response<RequestCodeResponse>

    @POST("api/auth/verify")
    suspend fun verify(@Body body: VerifyBody): Response<VerifyResponse>

    @GET("api/tournaments/mine")
    suspend fun getMyTournaments(): Response<TournamentsListDto>

    @GET("api/tournaments/{id}")
    suspend fun getTournament(@Path("id") id: Long): Response<TournamentDto>

    @POST("api/tournaments")
    suspend fun createTournament(@Body body: CreateTournamentBody): Response<TournamentDto>
}
