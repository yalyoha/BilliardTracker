package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ParticipantDto

@Composable
fun RefereePanel(
    participants: List<ParticipantDto>,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
    onUndo: () -> Unit,
    onFinish: (winnerPid: Long?) -> Unit,
) {
    val selectedPidState = remember { mutableStateOf(participants.firstOrNull()?.id ?: 0L) }
    val selectedPid = selectedPidState.value

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Активный игрок", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            participants.forEach { p ->
                FilterChip(
                    selected = p.id == selectedPid,
                    onClick = { selectedPidState.value = p.id },
                    label = { Text(p.displayName) },
                )
            }
        }

        Divider()
        Text("Забитый шар", style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(180.dp),
        ) {
            items((1..15).toList()) { ball ->
                OutlinedButton(onClick = { onShot(selectedPid, "ball", ball, 1) }) {
                    Text("$ball")
                }
            }
        }

        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onShot(selectedPid, "svoiak", null, 1) },
                modifier = Modifier.weight(1f),
            ) { Text("Свояк") }
            OutlinedButton(
                onClick = { onShot(selectedPid, "foul", null, -1) },
                modifier = Modifier.weight(1f),
            ) { Text("Штраф") }
            OutlinedButton(
                onClick = { onShot(selectedPid, "ball_out", null, -1) },
                modifier = Modifier.weight(1f),
            ) { Text("За борт") }
        }

        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                Text("Отменить последний")
            }
            Button(onClick = { onFinish(selectedPid) }, modifier = Modifier.weight(1f)) {
                Text("Партия окончена")
            }
        }
    }
}
