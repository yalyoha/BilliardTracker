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

enum class TileLayout { SplitVertical, Grid2x2, VerticalList }

/**
 * Портрет-режим (v1.22.0 не поддерживает landscape). Правила:
 *  - 2 игрока → split top/bottom (SplitVertical): каждая плитка ½ высоты.
 *  - 3–4 игрока → 2×2 grid (Grid2x2): для 3-х четвёртая ячейка = «Действия партии».
 *  - 5+ / 1 / 0 → VerticalList: скроллящийся список плиток ~20% высоты каждая.
 */
fun layoutFor(participantCount: Int): TileLayout = when (participantCount) {
    2 -> TileLayout.SplitVertical
    3, 4 -> TileLayout.Grid2x2
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
        TileLayout.Grid2x2 -> Column(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            participants.chunked(2).forEach { rowPair ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPair.forEach { p -> Row(Modifier.weight(1f)) { tileContent(p) } }
                    if (rowPair.size == 1) Row(Modifier.weight(1f)) {}
                }
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
