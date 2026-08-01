package com.example.billiardtracker.ui.screens.gametype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.domain.rules.RuleProfile
import com.example.billiardtracker.ui.nav.NewTournamentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StakeUiState(
    val gameTypeName: String = "",
    val gameTypeSlug: String = "",
    val moneyPlayable: Boolean = false,
    val title: String = "",
    val stakeRub: String = "",
    val winsRequired: Int = 3,
    val perParticipant: List<ParticipantStakeUi> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val createdTournamentId: Long? = null,
)

data class ParticipantStakeUi(
    val displayName: String,
    val phone: String?,
    val handicapPoints: Int = 0,
    val overrideRub: String = "", // empty = use base stake
)

class StakeSetupViewModel(
    private val newTournamentState: NewTournamentState,
    private val repo: TournamentRepository,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    private val _ui = MutableStateFlow(StakeUiState())
    val ui: StateFlow<StakeUiState> = _ui.asStateFlow()

    init {
        loadFromState()
    }

    fun loadFromState() {
        val slug = newTournamentState.gameType.value
        if (slug.isNullOrBlank()) return
        val gt = GameType.entries.firstOrNull { it.ruleFileSlug == slug } ?: return
        val profile = RuleProfile.forType(gt)
        val parts = newTournamentState.participants.value
        _ui.value = StakeUiState(
            gameTypeName = gt.displayName,
            gameTypeSlug = slug,
            moneyPlayable = profile.moneyPlayable,
            title = newTournamentState.title.value ?: "",
            winsRequired = newTournamentState.winsRequired.value ?: 3,
            perParticipant = parts.map {
                ParticipantStakeUi(displayName = it.displayName, phone = it.phone)
            },
        )
    }

    fun setTitle(v: String) { _ui.value = _ui.value.copy(title = v) }
    fun setStake(v: String) { _ui.value = _ui.value.copy(stakeRub = v.filter { it.isDigit() }) }
    fun setWinsRequired(v: Int) { _ui.value = _ui.value.copy(winsRequired = v.coerceIn(1, 10)) }
    fun setHandicap(idx: Int, v: Int) {
        val list = _ui.value.perParticipant.toMutableList()
        list[idx] = list[idx].copy(handicapPoints = v)
        _ui.value = _ui.value.copy(perParticipant = list)
    }
    fun setOverride(idx: Int, v: String) {
        val list = _ui.value.perParticipant.toMutableList()
        list[idx] = list[idx].copy(overrideRub = v.filter { it.isDigit() })
        _ui.value = _ui.value.copy(perParticipant = list)
    }

    fun submit() {
        val slug = _ui.value.gameTypeSlug.ifBlank { newTournamentState.gameType.value.orEmpty() }
        if (slug.isBlank()) {
            _ui.value = _ui.value.copy(error = "Игра не выбрана — вернись назад и выбери")
            return
        }
        val moneyKop = _ui.value.stakeRub.toLongOrNull()?.times(100)
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val activeTokenId = userPrefs.getActiveTokenId()
            val body = CreateTournamentBody(
                title = _ui.value.title.takeIf { it.isNotBlank() },
                gameType = slug,
                moneyPerBallKop = moneyKop,
                winsRequired = _ui.value.winsRequired,
                masterTokenId = activeTokenId,
                participants = _ui.value.perParticipant.map { p ->
                    CreateParticipantBody(
                        phone = p.phone,
                        displayName = p.displayName,
                        handicapPoints = p.handicapPoints,
                        perBallOverrideKop = p.overrideRub.toLongOrNull()?.times(100),
                    )
                },
            )
            repo.create(body).fold(
                onSuccess = { dto ->
                    newTournamentState.reset()
                    _ui.value = _ui.value.copy(loading = false, createdTournamentId = dto.id)
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Ошибка")
                }
            )
        }
    }
}
