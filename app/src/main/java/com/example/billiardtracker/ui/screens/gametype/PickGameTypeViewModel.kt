package com.example.billiardtracker.ui.screens.gametype

import androidx.lifecycle.ViewModel
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.ui.nav.NewTournamentState

class PickGameTypeViewModel(private val newTournamentState: NewTournamentState) : ViewModel() {
    val gameTypes: List<GameType> = GameType.entries

    fun choose(gt: GameType, onNext: () -> Unit) {
        newTournamentState.gameType.value = gt.ruleFileSlug
        onNext()
    }
}
