package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ParticipantDto

@Composable
fun RefereePanel(
    participants: List<ParticipantDto>,
    currentUserId: Long,
    myLocalName: String?,
    pottedBalls: Set<Int>,
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
                    label = { Text(p.effectiveName(currentUserId, myLocalName)) },
                )
            }
        }

        Divider()
        Text("Забитый шар", style = MaterialTheme.typography.titleSmall)
        // 3 ряда по 5 кнопок — обычные Rows чтобы работал nested-scroll
        // внутри verticalScroll в TournamentScreen. LazyVerticalGrid тут ломает layout.
        for (row in 0..2) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (col in 0..4) {
                    val ball = row * 5 + col + 1
                    val potted = ball in pottedBalls
                    OutlinedButton(
                        onClick = { onShot(selectedPid, "ball", ball, 1) },
                        enabled = !potted,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "$ball",
                            textDecoration = if (potted) TextDecoration.LineThrough else null,
                        )
                    }
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
