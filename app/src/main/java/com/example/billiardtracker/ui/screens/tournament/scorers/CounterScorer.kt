package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.domain.rules.RuleProfile

/**
 * ±1 счётчик для дисциплин группы B (пирамиды с равноценными шарами).
 * `+`  → onShot(pid, kind="ball", ballNumber=null, pointsDelta=+1).
 * `−`  → onDecrement(pid); disabled когда currentScore == 0.
 * `Свояк` (если profile.allowsSvoiak) → onShot(pid, "svoiak", null, +1).
 * `Штраф` (всегда — «за борт» / промах биком) → onShot(pid, "foul", null, -1).
 */
@Composable
fun CounterScorer(
    pid: Long,
    profile: RuleProfile,
    currentScore: Int,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
    onDecrement: (participantId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onDecrement(pid) },
                enabled = currentScore > 0,
                modifier = Modifier.weight(1f),
            ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
            Button(
                onClick = { onShot(pid, "ball", null, +1) },
                modifier = Modifier.weight(1f),
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profile.allowsSvoiak) {
                OutlinedButton(
                    onClick = { onShot(pid, "svoiak", null, +1) },
                    modifier = Modifier.weight(1f),
                ) { Text("Свояк") }
            }
            OutlinedButton(
                onClick = { onShot(pid, "foul", null, -1) },
                modifier = Modifier.weight(1f),
            ) { Text("Штраф") }
        }
    }
}
