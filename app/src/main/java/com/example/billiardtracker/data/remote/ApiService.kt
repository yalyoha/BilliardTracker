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
import com.example.billiardtracker.data.remote.dto.RegisterBody
import com.example.billiardtracker.data.remote.dto.RequestCodeBody
import com.example.billiardtracker.data.remote.dto.RequestCodeResponse
import com.example.billiardtracker.data.remote.dto.RotateTokenResponse
import com.example.billiardtracker.data.remote.dto.RulesListDto
import com.example.billiardtracker.data.remote.dto.ShotDto
import com.example.billiardtracker.data.remote.dto.ShotsListDto
import com.example.billiardtracker.data.remote.dto.SubscribeTokenBody
import com.example.billiardtracker.data.remote.dto.TokenDto
import com.example.billiardtracker.data.remote.dto.TokensListDto
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.remote.dto.TournamentsListDto
import com.example.billiardtracker.data.remote.dto.UpdateTokenBody
import com.example.billiardtracker.data.remote.dto.VerifyBody
import com.example.billiardtracker.data.remote.dto.VerifyResponse
import com.example.billiardtracker.data.remote.dto.VersionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/request-code")
    suspend fun requestCode(@Body body: RequestCodeBody): Response<RequestCodeResponse>

    @POST("api/auth/verify")
    suspend fun verify(@Body body: VerifyBody): Response<VerifyResponse>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): Response<VerifyResponse>

    @GET("api/tournaments/mine")
    suspend fun getMyTournaments(
        @Query("tokenId") tokenId: Long? = null,
    ): Response<TournamentsListDto>

    @GET("api/tournaments/{id}")
    suspend fun getTournament(@Path("id") id: Long): Response<TournamentDto>

    @POST("api/tournaments")
    suspend fun createTournament(@Body body: CreateTournamentBody): Response<TournamentDto>

    @DELETE("api/tournaments/{id}")
    suspend fun deleteTournament(@Path("id") id: Long): Response<Unit>

    @POST("api/tournaments/{id}/finish")
    suspend fun finishTournament(@Path("id") id: Long): Response<TournamentDto>

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

    @POST("api/tournaments/{id}/transfer-referee")
    suspend fun transferReferee(
        @Path("id") id: Long,
        @Body body: Map<String, Long>,
    ): Response<Unit>

    @GET("api/rules")
    suspend fun listRules(): Response<RulesListDto>

    @GET("api/rules/{slug}")
    suspend fun getRuleMarkdown(@Path("slug") slug: String): Response<ResponseBody>

    @GET("version.json")
    suspend fun getVersionJson(): Response<VersionDto>

    @GET("api/stats/me")
    suspend fun getMyStats(
        @Query("tokenId") tokenId: Long? = null,
    ): Response<com.example.billiardtracker.data.remote.dto.StatsMeDto>

    @GET("api/clubs")
    suspend fun listClubs(
        @Query("near") near: String? = null,
        @Query("radiusM") radiusM: Int? = null,
    ): Response<ClubsListDto>

    @POST("api/clubs")
    suspend fun createClub(@Body body: CreateClubBody): Response<ClubDto>

    @PATCH("api/clubs/{id}")
    suspend fun updateClub(
        @Path("id") id: Long,
        @Body body: com.example.billiardtracker.data.remote.dto.UpdateClubBody,
    ): Response<ClubDto>

    @DELETE("api/clubs/{id}")
    suspend fun deleteClub(@Path("id") id: Long): Response<Unit>

    @POST("api/donations")
    suspend fun createDonation(@Body body: CreateDonationBody): Response<DonationDto>

    // Master-tokens (share paths for public read-only view of your games).
    @POST("api/tokens")
    suspend fun createToken(@Body body: CreateTokenBody): Response<TokenDto>

    /**
     * Idempotent: returns the caller's first owned token if any, otherwise
     * atomically creates a fresh "Путь мастера №1". Client uses this on
     * self-heal so two concurrent VM inits can't spawn duplicate rows.
     */
    @POST("api/tokens/ensure-default")
    suspend fun ensureDefaultToken(): Response<TokenDto>

    /**
     * Onboarding-only helper: create the first token using an explicit auth
     * header instead of the AuthInterceptor. Called *after* register but
     * *before* the JWT is persisted to prefs — atomicity guarantees we never
     * end up with a stored JWT but no path master.
     */
    @POST("api/tokens")
    suspend fun createTokenWithAuth(
        @retrofit2.http.Header("Authorization") authorization: String,
        @Body body: CreateTokenBody,
    ): Response<TokenDto>

    @GET("api/tokens/mine")
    suspend fun listTokens(): Response<TokensListDto>

    @POST("api/tokens/subscribe")
    suspend fun subscribeToken(@Body body: SubscribeTokenBody): Response<TokenDto>

    // Teams (пресеты) — привязаны к master_token, синхронизируются между
    // владельцем и всеми подписчиками.
    @GET("api/tokens/{tokenId}/teams")
    suspend fun listTeams(
        @Path("tokenId") tokenId: Long,
    ): Response<com.example.billiardtracker.data.remote.dto.TeamsListDto>

    @POST("api/tokens/{tokenId}/teams")
    suspend fun createTeam(
        @Path("tokenId") tokenId: Long,
        @Body body: com.example.billiardtracker.data.remote.dto.CreateTeamBody,
    ): Response<com.example.billiardtracker.data.remote.dto.TeamDto>

    @PATCH("api/tokens/{tokenId}/teams/{teamId}")
    suspend fun renameTeam(
        @Path("tokenId") tokenId: Long,
        @Path("teamId") teamId: Long,
        @Body body: com.example.billiardtracker.data.remote.dto.RenameTeamBody,
    ): Response<com.example.billiardtracker.data.remote.dto.TeamDto>

    @DELETE("api/tokens/{tokenId}/teams/{teamId}")
    suspend fun deleteTeam(
        @Path("tokenId") tokenId: Long,
        @Path("teamId") teamId: Long,
    ): Response<Unit>

    @POST("api/tokens/{tokenId}/teams/{teamId}/members")
    suspend fun addTeamMember(
        @Path("tokenId") tokenId: Long,
        @Path("teamId") teamId: Long,
        @Body body: com.example.billiardtracker.data.remote.dto.AddTeamMemberBody,
    ): Response<com.example.billiardtracker.data.remote.dto.TeamMemberDto>

    @DELETE("api/tokens/{tokenId}/teams/{teamId}/members/{memberId}")
    suspend fun deleteTeamMember(
        @Path("tokenId") tokenId: Long,
        @Path("teamId") teamId: Long,
        @Path("memberId") memberId: Long,
    ): Response<Unit>

    @POST("api/tokens/{id}/rotate")
    suspend fun rotateToken(@Path("id") id: Long): Response<RotateTokenResponse>

    @PATCH("api/tokens/{id}")
    suspend fun updateToken(
        @Path("id") id: Long,
        @Body body: UpdateTokenBody,
    ): Response<TokenDto>

    @DELETE("api/tokens/{id}")
    suspend fun deleteToken(@Path("id") id: Long): Response<Unit>

    // Dev telemetry — временный эндпоинт для отладки.
    @POST("api/dev-log")
    suspend fun sendDevLog(
        @Body body: com.example.billiardtracker.data.remote.dto.DevLogBatchBody,
    ): Response<Unit>
}
