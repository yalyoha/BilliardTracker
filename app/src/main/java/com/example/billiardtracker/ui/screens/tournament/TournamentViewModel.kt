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

/**
 * Выбирает shot для отката при тапе «−» в CounterScorer: последний positive
 * (pointsDelta > 0) shot указанного участника в списке. Игнорирует штрафы
 * и off-table события — их откатывают через общий Undo, а не per-tile «−».
 * Extract'нута из VM ради юнит-теста (VM в целом требует Room/Retrofit fakes).
 */
internal fun pickShotToDecrement(shots: List<ShotDto>, pid: Long): ShotDto? =
    shots.lastOrNull { it.participantId == pid && it.pointsDelta > 0 }

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

    // Порядок ударов для Колхоза: список participantId в очерёдности хода.
    // null = ещё не инициализирован (до загрузки участников).
    private val _kolkhozOrder = MutableStateFlow<List<Long>?>(null)
    val kolkhozOrder: StateFlow<List<Long>?> = _kolkhozOrder.asStateFlow()

    fun initKolkhozOrder(participantIds: List<Long>) {
        if (_kolkhozOrder.value == null) {
            _kolkhozOrder.value = participantIds
        }
    }

    fun moveKolkhozPlayerUp(pid: Long) {
        val order = _kolkhozOrder.value ?: return
        val idx = order.indexOf(pid)
        if (idx <= 0) return
        val newOrder = order.toMutableList().also { it.removeAt(idx); it.add(idx - 1, pid) }
        _kolkhozOrder.value = newOrder
    }

    fun moveKolkhozPlayerDown(pid: Long) {
        val order = _kolkhozOrder.value ?: return
        val idx = order.indexOf(pid)
        if (idx < 0 || idx >= order.size - 1) return
        val newOrder = order.toMutableList().also { it.removeAt(idx); it.add(idx + 1, pid) }
        _kolkhozOrder.value = newOrder
    }

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
            val serverGames = gameRepo.listGames(tournamentId).getOrElse { emptyList() }
            // Сохраняем локально созданные (id < 0) игры, которые ещё не
            // синкнулись. Dedup по orderIndex: если сервер вернул game с
            // тем же orderIndex — это тот же game после sync, предпочтём
            // серверную версию (там актуальный id и scores).
            val localUnsynced = _ui.value.games.filter { g ->
                g.id < 0 && serverGames.none { it.orderIndex == g.orderIndex }
            }
            // Task 5 fix: если локально мы уже пометили игру как finished
            // (клиент нажал «Партия окончена»), а сервер ещё не отдал
            // обновление (finish_game op в outbox), — предпочитаем локальную
            // версию с winnerParticipantId. Иначе refresh «размораживает»
            // партию обратно в active, и 🏆 победитель пропадает из истории.
            val localFinished: Map<Long, GameDto> = _ui.value.games
                .filter { it.status == "finished" && it.winnerParticipantId != null }
                .associateBy { it.id }
            val mergedGames = serverGames.map { sg ->
                localFinished[sg.id]?.takeIf { sg.status != "finished" || sg.winnerParticipantId == null } ?: sg
            } + localUnsynced
            val currentLocal = _ui.value.currentGame
            val active = when {
                // Если currentGame был локальный и sync прошёл — переключиться
                // на серверную версию с тем же orderIndex.
                currentLocal != null && currentLocal.id < 0 -> {
                    serverGames.firstOrNull { it.orderIndex == currentLocal.orderIndex }
                        ?: currentLocal.takeIf { localUnsynced.any { l -> l.id == currentLocal.id } }
                        ?: mergedGames.lastOrNull { it.status == "active" }
                        ?: mergedGames.lastOrNull()
                }
                else -> mergedGames.lastOrNull { it.status == "active" } ?: mergedGames.lastOrNull()
            }
            // listShots мержит серверные + локальные pending (id<0) из Room.
            // После этого пересчитываем active.scores из shots — иначе сервер
            // отдаёт стale scores (без pending ударов), и счёт сбрасывается.
            val shots = if (active == null) emptyList()
                else gameRepo.listShots(active.id).getOrElse { emptyList() }
            val activeWithScores = active?.let { g ->
                if (shots.isEmpty()) g
                else {
                    val recalc = shots
                        .groupBy { it.participantId }
                        .mapValues { (_, list) -> list.sumOf { it.pointsDelta } }
                    g.copy(scores = recalc.map {
                        com.example.billiardtracker.data.remote.dto.ScoreDto(it.key, it.value)
                    })
                }
            }
            _ui.value = _ui.value.copy(
                loading = false,
                tournament = t,
                games = mergedGames,
                currentGame = activeWithScores,
                currentGameShots = shots,
            )
        }.onFailure {
            _ui.value = _ui.value.copy(loading = false, error = it.message)
        }
    }

    fun startGame() {
        viewModelScope.launch {
            gameRepo.startGame(tournamentId).onSuccess { newGame ->
                // Optimistic UI: локально созданная игра сразу становится currentGame.
                // Refresh() потом смёржит с серверными играми.
                _ui.value = _ui.value.copy(
                    games = _ui.value.games + newGame,
                    currentGame = newGame,
                    currentGameShots = emptyList(),
                )
                refresh()
            }
        }
    }

    fun addShot(participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) {
        val gid = _ui.value.currentGame?.id ?: return
        viewModelScope.launch {
            gameRepo.addShot(gid, participantId, kind, ballNumber, pointsDelta).onSuccess { shot ->
                // Optimistic UI: удар сразу отображается локально с локальным
                // (отрицательным) ID. Пересчитываем currentGame.scores чтобы
                // счёт партии обновился без ожидания сервера / SSE.
                val newShots = _ui.value.currentGameShots + shot
                val updatedGame = _ui.value.currentGame?.let { g ->
                    val by = newShots.filter { it.gameId == g.id }
                        .groupBy { it.participantId }
                        .mapValues { (_, list) -> list.sumOf { it.pointsDelta } }
                    g.copy(scores = by.map {
                        com.example.billiardtracker.data.remote.dto.ScoreDto(it.key, it.value)
                    })
                }
                _ui.value = _ui.value.copy(
                    lastShotIdPerGame = _ui.value.lastShotIdPerGame + (gid to shot.id),
                    currentGameShots = newShots,
                    currentGame = updatedGame,
                )
                // Refresh с сервера — обновит other-user shots + позже подставит
                // серверный ID для только что добавленного через SyncManager.
                refresh()
            }
        }
    }

    fun undoLastShot() {
        val gid = _ui.value.currentGame?.id ?: return
        val sid = _ui.value.lastShotIdPerGame[gid] ?: return
        viewModelScope.launch {
            gameRepo.deleteShot(gid, sid).onSuccess {
                // Optimistic UI: убираем локально + пересчитываем scores.
                val newShots = _ui.value.currentGameShots.filterNot { it.id == sid }
                val updatedGame = _ui.value.currentGame?.let { g ->
                    val by = newShots.filter { it.gameId == g.id }
                        .groupBy { it.participantId }
                        .mapValues { (_, list) -> list.sumOf { it.pointsDelta } }
                    g.copy(scores = by.map {
                        com.example.billiardtracker.data.remote.dto.ScoreDto(it.key, it.value)
                    })
                }
                _ui.value = _ui.value.copy(
                    currentGameShots = newShots,
                    currentGame = updatedGame,
                    lastShotIdPerGame = _ui.value.lastShotIdPerGame - gid,
                )
                refresh()
            }
        }
    }

    fun finishGame(winnerPid: Long? = null) {
        val game = _ui.value.currentGame ?: return
        // Auto-pick winner as highest scorer if caller didn't specify one.
        // Ties break to the participant that appears first in the tournament
        // — deterministic, and matches the order users see in the scoreboard.
        val gameType = _ui.value.tournament?.gameType ?: ""
        // Для этих дисциплин победитель — кто забил больше шаров (pointsDelta > 0),
        // штрафы не влияют на победителя (они финансовые, а не скоринговые для победы).
        val usePotCount = gameType in setOf(
            "svobodnaya-piramida", "kombinirovannaya-piramida", "dinamichnaya-piramida"
        )
        val resolvedWinner = winnerPid ?: run {
            val partIdOrder = _ui.value.tournament?.participants
                ?.mapIndexed { i, p -> p.id to i }?.toMap() ?: emptyMap()
            if (usePotCount) {
                val potsByPid = _ui.value.currentGameShots
                    .filter { it.pointsDelta > 0 }
                    .groupBy { it.participantId }
                    .mapValues { (_, shots) -> shots.sumOf { it.pointsDelta } }
                potsByPid.entries
                    .maxWithOrNull(
                        compareBy<Map.Entry<Long, Int>> { it.value }
                            .thenByDescending { partIdOrder[it.key] ?: Int.MAX_VALUE },
                    )?.key
            } else {
                game.scores
                    .maxWithOrNull(
                        compareBy<com.example.billiardtracker.data.remote.dto.ScoreDto> { it.points }
                            .thenByDescending { partIdOrder[it.participantId] ?: Int.MAX_VALUE },
                    )?.participantId
            }
        }
        // Task 5 fix: оптимистично помечаем партию finished + winner прямо
        // в _ui. refresh() ниже потом смёржит с сервером; если сервер ещё не
        // получил finish_game op (в outbox), нашу локальную версию оставит
        // (см. refresh: localFinished-map). Иначе 2-я, 3-я и т.д. партии
        // «мигали» — победитель появлялся только после SSE-эха.
        val finishedGame = game.copy(
            status = "finished",
            winnerParticipantId = resolvedWinner,
            finishedAt = System.currentTimeMillis(),
        )
        _ui.value = _ui.value.copy(
            games = _ui.value.games.map { if (it.id == game.id) finishedGame else it },
            currentGame = finishedGame,
        )
        viewModelScope.launch {
            gameRepo.finishGame(tournamentId, game.id, resolvedWinner).onSuccess { refresh() }
        }
    }

    /**
     * «−» в CounterScorer: удаляет последний positive shot указанного игрока.
     * Отличается от [undoLastShot], который тянет любой последний shot независимо
     * от игрока. Disabled на UI-уровне когда `pickShotToDecrement()` вернёт null.
     */
    fun decrementScore(pid: Long) {
        val gid = _ui.value.currentGame?.id ?: return
        val shot = pickShotToDecrement(_ui.value.currentGameShots, pid) ?: return
        viewModelScope.launch {
            gameRepo.deleteShot(gid, shot.id).onSuccess {
                val newShots = _ui.value.currentGameShots.filterNot { it.id == shot.id }
                val updatedGame = _ui.value.currentGame?.let { g ->
                    val by = newShots.filter { it.gameId == g.id }
                        .groupBy { it.participantId }
                        .mapValues { (_, list) -> list.sumOf { it.pointsDelta } }
                    g.copy(scores = by.map {
                        com.example.billiardtracker.data.remote.dto.ScoreDto(it.key, it.value)
                    })
                }
                _ui.value = _ui.value.copy(
                    currentGameShots = newShots,
                    currentGame = updatedGame,
                )
                refresh()
            }
        }
    }

    fun claimReferee() {
        viewModelScope.launch {
            gameRepo.claimReferee(tournamentId).onSuccess { refresh() }
        }
    }

    fun transferReferee(toUserId: Long) {
        viewModelScope.launch {
            gameRepo.transferReferee(tournamentId, toUserId).onSuccess { refresh() }
        }
    }

    fun closeTournament(onDone: () -> Unit) {
        viewModelScope.launch {
            tournamentRepo.finish(tournamentId).onSuccess {
                refresh()
                onDone()
            }
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

    /**
     * Tournament-wide payout: aggregates scores across every finished game
     * (not just the last one). This is what the PayoutScreen shows —
     * per-game payouts wouldn't reflect who actually owes whom by the end.
     */
    val tournamentPayout: PayoutResult?
        get() {
            val t = _ui.value.tournament ?: return null
            val games = _ui.value.games
            if (games.isEmpty()) return null
            val shots = games.flatMap { it.scores }.flatMap { s ->
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
