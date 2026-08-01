package com.example.billiardtracker.ui.screens.gametype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import com.example.billiardtracker.data.remote.dto.CreateTournamentBody
import com.example.billiardtracker.data.repo.TournamentRepository
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.domain.rules.RuleProfile
import com.example.billiardtracker.domain.usecase.DetectClubUseCase
import com.example.billiardtracker.ui.nav.NewTournamentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StakeUiState(
    val gameTypeName: String = "",
    val gameTypeSlug: String = "",
    val moneyPlayable: Boolean = false,
    val title: String = "",
    val stakeRub: String = "100",
    val winsRequired: Int = 3,
    val perParticipant: List<ParticipantStakeUi> = emptyList(),
    val nearbyClubs: List<ClubDto> = emptyList(),
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
    private val detectClub: DetectClubUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(StakeUiState())
    val ui: StateFlow<StakeUiState> = _ui.asStateFlow()

    init {
        loadFromState()
        viewModelScope.launch { fetchNearbyAndAutoFill() }
    }

    fun loadFromState() {
        val slug = newTournamentState.gameType.value
        if (slug.isNullOrBlank()) return
        val gt = GameType.entries.firstOrNull { it.ruleFileSlug == slug } ?: return
        val profile = RuleProfile.forType(gt)
        val parts = newTournamentState.participants.value
        _ui.value = _ui.value.copy(
            gameTypeName = gt.displayName,
            gameTypeSlug = slug,
            moneyPlayable = profile.moneyPlayable,
            title = newTournamentState.title.value ?: _ui.value.title,
            stakeRub = "100",
            winsRequired = newTournamentState.winsRequired.value ?: 3,
            perParticipant = parts.map {
                ParticipantStakeUi(displayName = it.displayName, phone = it.phone)
            },
        )
    }

    /**
     * Загружаем список ближайших клубов и (если название пустое) авто-заполняем
     * его по ближайшему из них. Пользователь может переопределить пикером
     * или ввести название руками.
     */
    private suspend fun fetchNearbyAndAutoFill() {
        val nearby = detectClub.nearby(radiusM = 5000)
        _ui.value = _ui.value.copy(nearbyClubs = nearby)
        if (_ui.value.title.isBlank() && nearby.isNotEmpty()) {
            applyClubToTitle(nearby.first())
        }
    }

    /** Re-run after user granted GPS-permission. */
    fun retryAutoTitle() {
        viewModelScope.launch { fetchNearbyAndAutoFill() }
    }

    fun pickClub(club: ClubDto) {
        viewModelScope.launch { applyClubToTitle(club) }
    }

    /**
     * "[Клуб] (N)" — N = следующий свободный номер среди уже созданных
     * турниров с этим клубом. Скобки вместо №, чтобы визуально не путать
     * с номером бара.
     */
    private suspend fun applyClubToTitle(club: ClubDto) {
        val prefix = club.name
        // matches "Название (12)" — только вариант со скобками; старые "№ N"
        // тоже учитываем чтобы не сталкиваться с уже созданными.
        val re = Regex("^${Regex.escape(prefix)}\\s*(?:\\((\\d+)\\)|№\\s*(\\d+))\$")
        val existing = repo.observeAll().first()
            .mapNotNull { it.title }
            .mapNotNull { re.matchEntire(it)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() } }
        val nextN = (existing.maxOrNull() ?: 0) + 1
        _ui.value = _ui.value.copy(title = "$prefix ($nextN)")
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
