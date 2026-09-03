package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Нижняя панель под MatchLayout: undo + finish + target-hint.
 * Confirm-диалог на «Партия окончена» защищает от misdaction — spec §2.7 «скорее да».
 * `targetHint` — человекочитаемая строка, VM формирует («до 8 побед · A 3 · B 2»
 * или «до 8 · A 5 · B 3» для дисциплин с ball-target).
 */
@Composable
fun MatchBottomBar(
    targetHint: String,
    winnerName: String?,
    winnerScore: Int?,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    if (isLandscape) {
        Row(
            modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                targetHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(0.8f)) {
                Text("↶ Отменить")
            }
            Button(onClick = { confirmOpen = true }, modifier = Modifier.weight(1f)) {
                Text("Партия окончена")
            }
        }
    } else {
        Column(
            modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                targetHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                    Text("↶ Отменить")
                }
                Button(onClick = { confirmOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Партия окончена")
                }
            }
        }
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Завершить партию?") },
            text = {
                val msg = when {
                    winnerName != null && winnerScore != null ->
                        "Победитель — $winnerName ($winnerScore)."
                    else -> "Победитель определится по текущему счёту."
                }
                Text(msg)
            },
            confirmButton = {
                Button(onClick = { confirmOpen = false; onFinish() }) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
}
