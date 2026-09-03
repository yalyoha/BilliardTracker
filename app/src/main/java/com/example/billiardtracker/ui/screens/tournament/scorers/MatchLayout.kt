package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ParticipantDto

enum class TileLayout { SplitVertical, VerticalList }

/**
 * Плитка всегда во всю ширину экрана:
 *  - 2–4 игрока → split (SplitVertical): плитки делят высоту поровну.
 *  - 5+ / 1 / 0 → VerticalList: скроллящийся список плиток.
 * Высота родительского Box вычисляется в TournamentScreen адаптивно под
 * реальную высоту экрана (поддерживает авторотацию).
 */
fun layoutFor(participantCount: Int): TileLayout = when (participantCount) {
    2, 3, 4 -> TileLayout.SplitVertical
    else -> TileLayout.VerticalList
}

/**
 * Раскладывает `tileContent(participant)` по правилам [layoutFor]. Не знает
 * ничего о scorer-логике — только про геометрию.
 */
@Composable
fun MatchLayout(
    participants: List<ParticipantDto>,
    modifier: Modifier = Modifier,
    tileContent: @Composable (ParticipantDto) -> Unit,
) {
    when (layoutFor(participants.size)) {
        TileLayout.SplitVertical -> Column(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            participants.forEach { p ->
                Row(Modifier.fillMaxWidth().weight(1f)) { tileContent(p) }
            }
        }
        TileLayout.VerticalList -> LazyColumn(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(participants, key = { it.id }) { p -> tileContent(p) }
        }
    }
}
