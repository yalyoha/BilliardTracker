package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.remote.dto.ClaimRefereeResponse
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.data.remote.dto.ClubsListDto
import com.example.billiardtracker.data.remote.dto.CreateClubBody
import com.example.billiardtracker.data.remote.dto.CreateDonationBody
import com.example.billiardtracker.data.remote.dto.CreateShotBody
import com.example.billiardtracker.data.remote.dto.CreateTokenBody
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.remote.dto.DonationDto
import com.example.billiardtracker.data.remote.dto.FinishGameBody
import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.GamesListDto
import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.RequestCodeResponse
import com.example.billiardtracker.data.remote.dto.RotateTokenResponse
import com.example.billiardtracker.data.remote.dto.RulesListDto
import com.example.billiardtracker.data.remote.dto.ShotDto
import com.example.billiardtracker.data.remote.dto.ShotsListDto
import com.example.billiardtracker.data.remote.dto.TokenDto
import com.example.billiardtracker.data.remote.dto.TokensListDto
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.remote.dto.TournamentsListDto
import com.example.billiardtracker.data.remote.dto.VerifyBody
import com.example.billiardtracker.data.remote.dto.VerifyResponse
import com.example.billiardtracker.data.remote.dto.VersionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/tournaments/{tid}/games")
    suspend fun listGames(@Path("tid") tid: Long): Response<GamesListDto>

    @POST("api/tournaments/{tid}/games")
    suspend fun startGame(
        @Path("tid") tid: Long,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<GameDto>

    @POST("api/tournaments/{tid}/games/{gid}/finish")
    suspend fun finishGame(
        @Path("tid") tid: Long,
        @Path("gid") gid: Long,
        @Body body: FinishGameBody,
    ): Response<GameDto>

    @POST("api/games/{gid}/shots")
    suspend fun addShot(@Path("gid") gid: Long, @Body body: CreateShotBody): Response<ShotDto>

    @DELETE("api/games/{gid}/shots/{sid}")
    suspend fun deleteShot(@Path("gid") gid: Long, @Path("sid") sid: Long): Response<Unit>

    @GET("api/games/{gid}/shots")
    suspend fun listShots(@Path("gid") gid: Long): Response<ShotsListDto>

    @POST("api/tournaments/{id}/claim-referee")
    suspend fun claimReferee(@Path("id") id: Long): Response<ClaimRefereeResponse>

    @GET("api/rules")
    suspend fun listRules(): Response<RulesListDto>

    @GET("api/rules/{slug}")
    suspend fun getRuleMarkdown(@Path("slug") slug: String): Response<ResponseBody>

    @GET("version.json")
    suspend fun getVersionJson(): Response<VersionDto>

    @GET("api/clubs")
    suspend fun listClubs(
        @Query("near") near: String? = null,
        @Query("radiusM") radiusM: Int? = null,
    ): Response<ClubsListDto>

    @POST("api/clubs")
    suspend fun createClub(@Body body: CreateClubBody): Response<ClubDto>

    @POST("api/donations")
    suspend fun createDonation(@Body body: CreateDonationBody): Response<DonationDto>

    // Master-tokens (share paths for public read-only view of your games).
    @POST("api/tokens")
    suspend fun createToken(@Body body: CreateTokenBody): Response<TokenDto>

    @GET("api/tokens/mine")
    suspend fun listTokens(): Response<TokensListDto>

    @POST("api/tokens/{id}/rotate")
    suspend fun rotateToken(@Path("id") id: Long): Response<RotateTokenResponse>

    @DELETE("api/tokens/{id}")
    suspend fun deleteToken(@Path("id") id: Long): Response<Unit>
}
