package com.example.billiardtracker.ui.nav

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import com.example.billiardtracker.data.remote.dto.TeamDto
import com.example.billiardtracker.data.remote.dto.TeamMemberDto
import com.example.billiardtracker.data.repo.TeamRepository
import com.example.billiardtracker.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Совместимость с UI-слоем: TeamMember и Team остаются как domain-типы,
// но теперь маппятся из/в серверные DTO.
data class TeamMember(val displayName: String, val phone: String?)

data class Team(
    val id: Long,
    val name: String,
    val players: List<TeamMember> = emptyList(),
)

/**
 * Хранилище команд — теперь server-sourced под активным master_token.
 * Синхронизируется с backend'ом на каждое переключение токена; все
 * подписчики видят один и тот же список.
 *
 * Живёт в AppContainer как singleton, переживает пересоздание Composable.
 */
class TeamState(
    private val userPrefs: UserPrefs,
    private val repo: TeamRepository,
    private val appScope: CoroutineScope,
    private val syncManager: SyncManager,
) {
    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    private val _activeTeamId = MutableStateFlow<Long?>(null)
    val activeTeamId: StateFlow<Long?> = _activeTeamId.asStateFlow()

    // Local→server id-remap событий, форвардится из SyncManager. UI-слой
    // (ViewModel) подписывается, чтобы обновить draft.expandedTeamId и не
    // «слетать» с редактирования после успешной синхронизации.
    val teamIdRemaps: SharedFlow<Pair<Long, Long>> = syncManager.teamIdRemaps

    init {
        // Восстанавливаем сохранённый activeTeamId из prefs при старте.
        appScope.launch { _activeTeamId.value = userPrefs.getActiveTeamId() }
        // Реактивно на смену активного токена — перезагружаем команды с сервера.
        // distinctUntilChanged критичен: DataStore.data эмитит на любой write,
        // а .map не дедуплицирует. Без него — fire-and-forget setActiveTeamId
        // из addTeam (первая команда) триггерит re-collect, repo.list уходит
        // на сервер ДО того как sync успел запостить create_team, возвращает
        // список без свежесозданной команды → _teams затирается → юзер видит
        // "экран сбросился, команда не создана" (баг v1.19.2 на Xiaomi Redmi
        // Note 14 Pro — быстрое устройство + быстрая сеть).
        appScope.launch {
            userPrefs.activeTokenIdFlow.distinctUntilChanged().collect { tokenId ->
                if (tokenId == null) {
                    _teams.value = emptyList()
                    return@collect
                }
                val list = repo.list(tokenId).getOrElse { emptyList() }
                // Merge: сохраняем локальные unsynced команды (id < 0), которые
                // сервер ещё не знает — иначе legit token-switch с pending sync
                // тоже их потеряет. Пары local↔server резолвит teamIdRemaps.
                val serverTeams = list.map { it.toDomain() }
                val localUnsynced = _teams.value.filter { it.id < 0 }
                _teams.value = serverTeams + localUnsynced
                // Убедиться что active team существует в новом списке; иначе
                // выбрать первый или сбросить.
                val stored = _activeTeamId.value
                if (stored == null || list.none { it.id == stored }) {
                    val next = list.firstOrNull()?.id
                    _activeTeamId.value = next
                    userPrefs.setActiveTeamId(next)
                }
            }
        }
        // Слушаем id-remaps от SyncManager: заменяем локальный id на серверный
        // в _teams и в _activeTeamId. Иначе после успешного sync карточка
        // «сворачивается» и последующие addPlayer шлют устаревший локальный id.
        appScope.launch {
            syncManager.teamIdRemaps.collect { (local, server) ->
                _teams.value = _teams.value.map { if (it.id == local) it.copy(id = server) else it }
                if (_activeTeamId.value == local) {
                    _activeTeamId.value = server
                    userPrefs.setActiveTeamId(server)
                }
            }
        }
    }

    private fun TeamDto.toDomain() = Team(
        id = id,
        name = name,
        players = members.map { it.toDomain() },
    )

    private fun TeamMemberDto.toDomain() = TeamMember(displayName = displayName, phone = phone)

    fun teamById(id: Long?): Team? = _teams.value.firstOrNull { it.id == id }

    fun asParticipantBodies(teamId: Long): List<CreateParticipantBody> =
        teamById(teamId)?.players?.map { m ->
            CreateParticipantBody(
                phone = m.phone,
                displayName = m.displayName,
                handicapPoints = 0,
            )
        }.orEmpty()

    suspend fun addTeam(name: String): Result<Team> {
        val tokenId = userPrefs.getActiveTokenId() ?: return Result.failure(IllegalStateException("no active token"))
        return repo.create(tokenId, name).map { dto ->
            val team = dto.toDomain()
            _teams.value = _teams.value + team
            if (_activeTeamId.value == null) {
                _activeTeamId.value = team.id
                // Fire-and-forget: suspend внутри .map даёт sync-корутине шанс
                // отработать до того, как VM успеет установить expandedTeamId
                // на возвращённый local id → id-remap эмитится с _draft.expandedTeamId==null,
                // не мэтчится, и после fold мы остаёмся со stale local id.
                appScope.launch { userPrefs.setActiveTeamId(team.id) }
            }
            team
        }
    }

    suspend fun renameTeam(id: Long, name: String): Result<Unit> {
        val tokenId = userPrefs.getActiveTokenId() ?: return Result.failure(IllegalStateException("no active token"))
        return repo.rename(tokenId, id, name).map { dto ->
            _teams.value = _teams.value.map { if (it.id == id) it.copy(name = dto.name) else it }
        }
    }

    suspend fun deleteTeam(id: Long): Result<Unit> {
        val tokenId = userPrefs.getActiveTokenId() ?: return Result.failure(IllegalStateException("no active token"))
        return repo.delete(tokenId, id).map {
            _teams.value = _teams.value.filterNot { it.id == id }
            if (_activeTeamId.value == id) {
                val next = _teams.value.firstOrNull()?.id
                _activeTeamId.value = next
                userPrefs.setActiveTeamId(next)
            }
        }
    }

    fun setActiveTeam(id: Long) {
        _activeTeamId.value = id
        appScope.launch { userPrefs.setActiveTeamId(id) }
    }

    /**
     * Добавляет игрока к команде. Возвращает paired id → serverMemberId, чтобы
     * UI мог его удалить позже.
     */
    suspend fun addPlayer(teamId: Long, name: String, phone: String?): Result<Unit> {
        val tokenId = userPrefs.getActiveTokenId() ?: return Result.failure(IllegalStateException("no active token"))
        return repo.addMember(tokenId, teamId, name, phone).map { dto ->
            val member = TeamMember(dto.displayName, dto.phone)
            _teams.value = _teams.value.map {
                if (it.id == teamId) it.copy(players = it.players + member) else it
            }
            // Сохраняем full team refresh чтобы member.id был доступен в кеше
            // (для удаления через removePlayerAt мы используем индекс + свежий
            // серверный список, см. removePlayerAt).
        }
    }

    suspend fun removePlayerAt(teamId: Long, index: Int): Result<Unit> {
        val tokenId = userPrefs.getActiveTokenId() ?: return Result.failure(IllegalStateException("no active token"))
        // Нужен серверный id участника — перезапрашиваем актуальный список
        // команды и берём member по индексу.
        val server = repo.list(tokenId).getOrElse { return Result.failure(IllegalStateException("failed to refresh")) }
        val team = server.firstOrNull { it.id == teamId } ?: return Result.failure(IllegalStateException("team not found"))
        val member = team.members.getOrNull(index) ?: return Result.failure(IllegalStateException("member not found"))
        return repo.removeMember(tokenId, teamId, member.id).map {
            _teams.value = _teams.value.map {
                if (it.id == teamId) it.copy(players = it.players.filterIndexed { i, _ -> i != index }) else it
            }
        }
    }

    /** Force-refresh с сервера. Merge с local unsynced (см. init.collect). */
    suspend fun refresh() {
        val tokenId = userPrefs.getActiveTokenId() ?: return
        val list = repo.list(tokenId).getOrElse { return }
        val serverTeams = list.map { it.toDomain() }
        val localUnsynced = _teams.value.filter { it.id < 0 }
        _teams.value = serverTeams + localUnsynced
    }
}
