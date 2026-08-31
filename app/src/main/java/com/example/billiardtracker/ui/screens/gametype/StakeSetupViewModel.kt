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
import com.example.billiardtracker.ui.nav.Team
import com.example.billiardtracker.ui.nav.TeamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v1.23.0: добавлены поля stakeMode (за шар|за встречу), gameSize (0..1000).
 * Также экран теперь сам управляет выбором состава — из TeamState читаем список
 * готовых составов и подставляем игроков активного как participants.
 */
data class StakeUiState(
    val gameTypeName: String = "",
    val gameTypeSlug: String = "",
    val moneyPlayable: Boolean = false,
    val title: String = "",
    val stakeRub: String = "100",
    val stakeMode: String = "per_ball",   // per_ball | per_match
    val gameSize: Int = 8,                // размер партии в шарах (0..1000)
    val winsRequired: Int = 3,
    val perParticipant: List<ParticipantStakeUi> = emptyList(),
    val nearbyClubs: List<ClubDto> = emptyList(),
    // v1.24.0 (task 1): владелец больше не добавляется автоматически. UI знает
    // локальный профиль (имя+телефон) и предлагает кнопку «Добавить владельца
    // телефона», если его ещё нет в списке участников.
    val ownerName: String = "",
    val ownerPhone: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val createdTournamentId: Long? = null,
) {
    /** true если владелец телефона уже в списке участников (по phone-match). */
    val ownerAlreadyIn: Boolean
        get() = ownerPhone != null && perParticipant.any {
            it.phone?.filter { c -> c.isDigit() } == ownerPhone.filter { c -> c.isDigit() }
        }
}

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
    private val teamState: TeamState,
) : ViewModel() {

    private val _ui = MutableStateFlow(StakeUiState())
    val ui: StateFlow<StakeUiState> = _ui.asStateFlow()

    // Прокидываем teams/activeTeamId из TeamState как есть — UI подписывается
    // напрямую на них через collectAsStateWithLifecycle.
    val teams = teamState.teams
    val activeTeamId = teamState.activeTeamId

    init {
        loadFromState()
        viewModelScope.launch { fetchNearbyAndAutoFill() }
        viewModelScope.launch { teamState.refresh() }
        // v1.24.6: реактивно на смену активного состава + правки в его игроках.
        // Раньше collect срабатывал только на activeTeamId; если teams-список
        // ещё не загрузился (refresh() параллельно летит), teamById() возвращал
        // null и заполнение молча пропускалось — блок «Участники» оставался
        // пустым до следующего тапа. Также adding player к активной команде
        // не обновлял perParticipant. combine over (activeTeamId × teams) чинит
        // оба кейса — эмитит при любом изменении.
        viewModelScope.launch {
            combine(teamState.activeTeamId, teamState.teams) { id, teams ->
                id?.let { active -> teams.firstOrNull { it.id == active } }
            }.collect { team ->
                if (team != null) mergeParticipantsFromTeam(team)
            }
        }
        // Подгружаем локальный профиль (name+phone) для кнопки «Добавить владельца».
        viewModelScope.launch {
            val name = userPrefs.getName().orEmpty()
            val phone = userPrefs.getPhone()
            _ui.value = _ui.value.copy(ownerName = name, ownerPhone = phone)
        }
    }

    fun loadFromState() {
        val slug = newTournamentState.gameType.value
        if (slug.isNullOrBlank()) return
        val gt = GameType.entries.firstOrNull { it.ruleFileSlug == slug } ?: return
        val profile = RuleProfile.forType(gt)
        val defaultSize = profile.winTargetPoints ?: profile.winTargetBalls ?: 8
        // Если participants уже были положены навигацией (legacy path) — берём их;
        // иначе позже fillParticipantsFromTeam заполнит из активного состава.
        val parts = newTournamentState.participants.value
        _ui.value = _ui.value.copy(
            gameTypeName = gt.displayName,
            gameTypeSlug = slug,
            moneyPlayable = profile.moneyPlayable,
            title = newTournamentState.title.value ?: _ui.value.title,
            stakeRub = _ui.value.stakeRub.ifBlank { "100" },
            gameSize = defaultSize,
            winsRequired = newTournamentState.winsRequired.value ?: 3,
            perParticipant = if (parts.isEmpty()) _ui.value.perParticipant
                             else parts.map { ParticipantStakeUi(displayName = it.displayName, phone = it.phone) },
        )
    }

    /**
     * Синхронизирует perParticipant с игроками активного состава.
     * Владелец телефона (ownerPhone) ИСКЛЮЧАЕТСЯ из автозаполнения — он
     * появляется только при явном нажатии «Добавить себя». Если в текущей
     * сессии владелец уже был добавлен (есть в prev), он сохраняется в конце
     * списка.
     */
    private fun mergeParticipantsFromTeam(team: Team) {
        val digits: (String?) -> String = { it?.filter { c -> c.isDigit() }.orEmpty() }
        val key: (String, String?) -> String = { n, p -> "$n|${digits(p)}" }
        val ownerDigits = digits(_ui.value.ownerPhone)
        val prev = _ui.value.perParticipant
        val nonOwnerPlayers = team.players.filter { m ->
            ownerDigits.isEmpty() || digits(m.phone) != ownerDigits
        }
        val fromTeam = nonOwnerPlayers.map { m ->
            val k = key(m.displayName, m.phone)
            val old = prev.firstOrNull { key(it.displayName, it.phone) == k }
            ParticipantStakeUi(
                displayName = m.displayName,
                phone = m.phone,
                handicapPoints = old?.handicapPoints ?: 0,
                overrideRub = old?.overrideRub.orEmpty(),
            )
        }
        val teamKeys = fromTeam.map { key(it.displayName, it.phone) }.toSet()
        // Сохраняем участников, которых нет в команде (напр. владелец, добавленный в этой сессии).
        val extras = prev.filter { key(it.displayName, it.phone) !in teamKeys }
        _ui.value = _ui.value.copy(perParticipant = fromTeam + extras)
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
        val re = Regex("^${Regex.escape(prefix)}\\s*(?:\\((\\d+)\\)|№\\s*(\\d+))\$")
        val existing = repo.observeAll().first()
            .mapNotNull { it.title }
            .mapNotNull { re.matchEntire(it)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() } }
        val nextN = (existing.maxOrNull() ?: 0) + 1
        _ui.value = _ui.value.copy(title = "$prefix ($nextN)")
    }

    fun setTitle(v: String) { _ui.value = _ui.value.copy(title = v) }
    fun setStake(v: String) { _ui.value = _ui.value.copy(stakeRub = v.filter { it.isDigit() }) }
    fun setStakeMode(mode: String) {
        val normalized = if (mode == "per_match") "per_match" else "per_ball"
        _ui.value = _ui.value.copy(stakeMode = normalized)
    }
    fun setGameSize(v: Int) { _ui.value = _ui.value.copy(gameSize = v.coerceIn(0, 1000)) }
    fun incGameSize() { setGameSize(_ui.value.gameSize + 1) }
    fun decGameSize() { setGameSize(_ui.value.gameSize - 1) }
    fun setGameSizeText(v: String) {
        val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
        setGameSize(n)
    }
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

    /**
     * Добавляет владельца телефона в Участники и сохраняет в активный состав.
     * В perParticipant добавляется сразу (оптимистично). В команду записывается
     * только если его там ещё нет — чтобы не было дублей.
     * При следующей встрече владелец в команде будет, но mergeParticipantsFromTeam
     * его игнорирует (по номеру телефона) — он появится только при нажатии кнопки.
     */
    fun addOwnerAsParticipant() {
        val name = _ui.value.ownerName.ifBlank { "Я" }
        val phone = _ui.value.ownerPhone
        if (_ui.value.ownerAlreadyIn) return
        val teamId = teamState.activeTeamId.value ?: run {
            _ui.value = _ui.value.copy(error = "Сначала выбери или создай состав")
            return
        }
        _ui.value = _ui.value.copy(
            perParticipant = _ui.value.perParticipant + ParticipantStakeUi(
                displayName = name,
                phone = phone,
            )
        )
        val ownerDigits = phone?.filter { it.isDigit() }.orEmpty()
        val team = teamState.teamById(teamId)
        val alreadyInTeam = team != null && ownerDigits.isNotEmpty() &&
            team.players.any { it.phone?.filter { c -> c.isDigit() }.orEmpty() == ownerDigits }
        if (!alreadyInTeam) {
            viewModelScope.launch {
                teamState.addPlayer(teamId, name, phone).onFailure { e ->
                    _ui.value = _ui.value.copy(error = e.message ?: "Не удалось добавить владельца")
                }
            }
        }
    }

    /**
     * Убрать участника по индексу.
     * Если idx входит в диапазон игроков команды — удаляет из состава (persist).
     * Если это «экстра» (владелец, добавленный кнопкой) — убирает только из
     * perParticipant без изменения команды.
     */
    fun removeParticipant(idx: Int) {
        val teamId = teamState.activeTeamId.value
        val team = if (teamId != null) teamState.teamById(teamId) else null
        val ownerDigits = _ui.value.ownerPhone?.filter { it.isDigit() }.orEmpty()
        val nonOwnerSize = team?.players
            ?.filter { ownerDigits.isEmpty() || it.phone?.filter { c -> c.isDigit() }.orEmpty() != ownerDigits }
            ?.size ?: 0
        if (team != null && teamId != null && idx < nonOwnerSize) {
            viewModelScope.launch {
                teamState.removePlayerAt(teamId, idx).onFailure { e ->
                    _ui.value = _ui.value.copy(error = e.message ?: "Не удалось убрать игрока")
                }
            }
        } else {
            val list = _ui.value.perParticipant.toMutableList()
            if (idx in list.indices) {
                list.removeAt(idx)
                _ui.value = _ui.value.copy(perParticipant = list)
            }
        }
    }

    fun submit() {
        val slug = _ui.value.gameTypeSlug.ifBlank { newTournamentState.gameType.value.orEmpty() }
        if (slug.isBlank()) {
            _ui.value = _ui.value.copy(error = "Игра не выбрана — вернись назад и выбери")
            return
        }
        if (_ui.value.perParticipant.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Нет игроков — выбери или создай состав ниже")
            return
        }
        val moneyKop = _ui.value.stakeRub.toLongOrNull()?.times(100)
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val activeTokenId = userPrefs.getActiveTokenId()
            val currentUserId = userPrefs.getUserId() ?: 0
            val body = CreateTournamentBody(
                title = _ui.value.title.takeIf { it.isNotBlank() },
                gameType = slug,
                moneyPerBallKop = moneyKop,
                winsRequired = _ui.value.winsRequired,
                masterTokenId = activeTokenId,
                stakeMode = _ui.value.stakeMode,
                gameSize = _ui.value.gameSize.takeIf { it > 0 },
                participants = _ui.value.perParticipant.map { p ->
                    CreateParticipantBody(
                        phone = p.phone,
                        displayName = p.displayName,
                        handicapPoints = p.handicapPoints,
                        perBallOverrideKop = p.overrideRub.toLongOrNull()?.times(100),
                    )
                },
            )
            repo.create(body, currentUserId).fold(
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
