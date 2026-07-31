package com.example.billiardtracker.ui.nav

import com.example.billiardtracker.data.remote.dto.CreateParticipantBody
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Простое in-memory состояние flow создания турнира.
 * Task 3.5 записывает участников, Task 3.6 — gameType + ставки, потом submit.
 * Живёт в AppContainer как singleton — сбрасывается после успешного submit.
 */
class NewTournamentState {
    val participants = MutableStateFlow<List<CreateParticipantBody>>(emptyList())
    val title = MutableStateFlow<String?>(null)
    val gameType = MutableStateFlow<String?>(null)
    val moneyPerBallKop = MutableStateFlow<Long?>(null)

    fun reset() {
        participants.value = emptyList()
        title.value = null
        gameType.value = null
        moneyPerBallKop.value = null
    }
}
