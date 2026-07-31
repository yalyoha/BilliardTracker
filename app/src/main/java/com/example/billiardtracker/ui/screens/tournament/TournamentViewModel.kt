package com.example.billiardtracker.ui.screens.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.SseClient
import com.example.billiardtracker.data.remote.dto.CreateDonationBody
import com.example.billiardtracker.data.remote.dto.DonationDto
import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.ShotDto
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.data.repo.DonationRepository
import com.example.billiardtracker.data.repo.GameRepository
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.domain.rules.PayoutCalculator
import com.example.billiardtracker.domain.rules.PayoutInputParticipant
import com.example.billiardtracker.domain.rules.PayoutInputShot
import com.example.billiardtracker.domain.rules.PayoutInputTournament
import com.example.billiardtracker.domain.rules.PayoutResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TournamentUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tournament: TournamentDto? = null,
    val currentGame: GameDto? = null,
    val games: List<GameDto> = emptyList(),
    val myUserId: Long = 0,
    val myLocalName: String? = null,
    val lastShotIdPerGame: Map<Long, Long> = emptyMap(),
    val currentGameShots: List<ShotDto> = emptyList(),
) {
    val isReferee: Boolean
        get() = tournament != null && tournament.refereeUserId == myUserId
}

class TournamentViewModel(
    private val tournamentId: Long,
    private val tournamentRepo: TournamentRepository,
    private val gameRepo: GameRepository,
    private val sseClient: SseClient,
    private val userPrefs: UserPrefs,
    private val donationRepo: DonationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TournamentUiState())
    val ui: StateFlow<TournamentUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = userPrefs.getUserId() ?: 0
            val name = userPrefs.getName()
            _ui.value = _ui.value.copy(myUserId = uid, myLocalName = name)
            refresh()
            sseClient.stream(tournamentId).collect { _ ->
                // On any event, re-fetch state. Simpler than surgical updates for MVP.
                refresh()
            }
        }
    }

    private suspend fun refresh() {
        tournamentRepo.fetchDetail(tournamentId).onSuccess { t ->
            val games = gameRepo.listGames(tournamentId).getOrElse { emptyList() }
            val active = games.lastOrNull { it.status == "active" } ?: games.lastOrNull()
            val shots = active?.id?.let { gid ->
                gameRepo.listShots(gid).getOrElse { emptyList() }
            } ?: emptyList()
            _ui.value = _ui.value.copy(
                loading = false,
                tournament = t,
                games = games,
                currentGame = active,
                currentGameShots = shots,
            )
        }.onFailure {
            _ui.value = _ui.value.copy(loading = false, error = it.message)
        }
    }

    fun startGame() {
        viewModelScope.launch {
            gameRepo.startGame(tournamentId).onSuccess { refresh() }
        }
    }

    fun addShot(participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) {
        val gid = _ui.value.currentGame?.id ?: return
        viewModelScope.launch {
            gameRepo.addShot(gid, participantId, kind, ballNumber, pointsDelta).onSuccess { shot ->
                _ui.value = _ui.value.copy(
                    lastShotIdPerGame = _ui.value.lastShotIdPerGame + (gid to shot.id),
                )
                refresh()
            }
        }
    }

    fun undoLastShot() {
        val gid = _ui.value.currentGame?.id ?: return
        val sid = _ui.value.lastShotIdPerGame[gid] ?: return
        viewModelScope.launch {
            gameRepo.deleteShot(gid, sid).onSuccess { refresh() }
        }
    }

    fun finishGame(winnerPid: Long?) {
        val gid = _ui.value.currentGame?.id ?: return
        viewModelScope.launch {
            gameRepo.finishGame(tournamentId, gid, winnerPid).onSuccess { refresh() }
        }
    }

    fun claimReferee() {
        viewModelScope.launch {
            gameRepo.claimReferee(tournamentId).onSuccess { refresh() }
        }
    }

    suspend fun donate(body: CreateDonationBody): Result<DonationDto> = donationRepo.create(body)

    /**
     * Payout по текущей партии — считается на клиенте из агрегированных счётов.
     *
     * Аппроксимация: GET /api/games/:gid отдаёт только суммарные scores, а не
     * сырые shots. Мы реконструируем pseudo-shots (по 1 pointsDelta на очко),
     * которые PayoutCalculator суммирует обратно в те же самые scores. Итог
     * математически идентичен передаче реальных shots, потому что калькулятор
     * учитывает только сумму pointsDelta на игрока.
     *
     * TODO (Task 3.8b+): когда backend выставит /api/games/:gid/shots, читать
     * настоящие shots для полной точности (важно для аудита штрафов и т.п.).
     */
    val payout: PayoutResult?
        get() {
            val t = _ui.value.tournament ?: return null
            val g = _ui.value.currentGame ?: return null
            val shots = g.scores.flatMap { s ->
                List(s.points.coerceAtLeast(0)) {
                    PayoutInputShot(participantId = s.participantId, kind = "ball", pointsDelta = 1)
                }
            }
            return PayoutCalculator.compute(
                tournament = PayoutInputTournament(id = t.id, moneyPerBallKop = t.moneyPerBallKop),
                participants = t.participants.map {
                    PayoutInputParticipant(
                        id = it.id,
                        handicapPoints = it.handicapPoints,
                        perBallOverrideKop = it.perBallOverrideKop,
                    )
                },
                shots = shots,
            )
        }
}
