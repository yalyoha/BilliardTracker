package com.example.billiardtracker.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.local.dao.GameDao
import com.example.billiardtracker.data.local.dao.ParticipantDao
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.data.remote.dto.StatsMeDto
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.domain.rules.GameType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: StatsMeDto? = null,
)

data class DisciplineStat(
    val slug: String,
    val displayName: String,
    val meetings: Int,
    val gamesPlayed: Int,
    val gamesWon: Int,
)

data class OpponentStat(
    val name: String,
    val meetings: Int,
    val gamesPlayed: Int,
    val gamesWon: Int,
)

data class LocalStats(
    val byDiscipline: List<DisciplineStat>,
    val byOpponent: List<OpponentStat>,
)

class StatsViewModel(
    private val api: ApiService,
    private val userPrefs: UserPrefs,
    private val tournamentRepo: TournamentRepository,
    private val gameDao: GameDao,
    private val participantDao: ParticipantDao,
) : ViewModel() {
    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    val finishedTournaments: StateFlow<List<TournamentEntity>> = tournamentRepo.observeAll()
        .map { list -> list.filter { it.status != "active" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _localStats = MutableStateFlow<LocalStats?>(null)
    val localStats: StateFlow<LocalStats?> = _localStats.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefs.activeTokenIdFlow.collect { tokenId ->
                fetch(tokenId)
            }
        }
        viewModelScope.launch {
            combine(
                tournamentRepo.observeAll().map { list -> list.filter { it.status != "active" } },
                userPrefs.userIdFlow,
            ) { tournaments, myUserId ->
                tournaments to myUserId
            }.collectLatest { (tournaments, myUserId) ->
                if (myUserId != null) {
                    _localStats.value = computeLocalStats(tournaments, myUserId)
                }
            }
        }
    }

    private suspend fun computeLocalStats(
        tournaments: List<TournamentEntity>,
        myUserId: Long,
    ): LocalStats {
        val allGames = gameDao.listAll()
        val allParticipants = participantDao.listAll()

        val gamesByTid = allGames.groupBy { it.tournamentId }
        val participantsByTid = allParticipants.groupBy { it.tournamentId }

        val discMeetings = mutableMapOf<String, Int>()
        val discPlayed = mutableMapOf<String, Int>()
        val discWon = mutableMapOf<String, Int>()

        val oppNames = mutableMapOf<String, String>()
        val oppMeetings = mutableMapOf<String, Int>()
        val oppPlayed = mutableMapOf<String, Int>()
        val oppWon = mutableMapOf<String, Int>()

        for (t in tournaments) {
            val participants = participantsByTid[t.id] ?: continue
            val myPart = participants.firstOrNull { it.userId == myUserId } ?: continue
            val games = (gamesByTid[t.id] ?: emptyList()).filter { it.status == "finished" }
            val played = games.size
            val won = games.count { it.winnerParticipantId == myPart.id }

            val slug = t.gameType
            discMeetings[slug] = (discMeetings[slug] ?: 0) + 1
            discPlayed[slug] = (discPlayed[slug] ?: 0) + played
            discWon[slug] = (discWon[slug] ?: 0) + won

            participants.filter { it.userId != myUserId }.forEach { opp ->
                val name = opp.displayName.takeIf { it.isNotBlank() } ?: "Игрок"
                val key = opp.userId?.toString() ?: "name:$name"
                oppNames[key] = name
                oppMeetings[key] = (oppMeetings[key] ?: 0) + 1
                oppPlayed[key] = (oppPlayed[key] ?: 0) + played
                oppWon[key] = (oppWon[key] ?: 0) + won
            }
        }

        return LocalStats(
            byDiscipline = discMeetings.keys
                .sortedByDescending { discMeetings[it]!! }
                .map { slug ->
                    DisciplineStat(
                        slug = slug,
                        displayName = GameType.entries.firstOrNull { it.ruleFileSlug == slug }?.displayName ?: slug,
                        meetings = discMeetings[slug]!!,
                        gamesPlayed = discPlayed[slug] ?: 0,
                        gamesWon = discWon[slug] ?: 0,
                    )
                },
            byOpponent = oppMeetings.keys
                .sortedByDescending { oppMeetings[it]!! }
                .map { key ->
                    OpponentStat(
                        name = oppNames[key] ?: key,
                        meetings = oppMeetings[key]!!,
                        gamesPlayed = oppPlayed[key] ?: 0,
                        gamesWon = oppWon[key] ?: 0,
                    )
                },
        )
    }

    private suspend fun fetch(tokenId: Long?) {
        _ui.value = _ui.value.copy(loading = true, error = null)
        try {
            val res = api.getMyStats(tokenId)
            if (res.isSuccessful) {
                _ui.value = StatsUiState(loading = false, stats = res.body())
            } else {
                _ui.value = _ui.value.copy(loading = false, error = "HTTP ${res.code()}")
            }
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Ошибка")
        }
    }
}
