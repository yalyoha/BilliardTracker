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
 * v1.24.0 layout (по фидбеку юзера):
 *   Row 1:  [    +    ] [ Штраф ]
 *   Row 2:  [  Чужой ] [  Свой  ]
 *
 * Семантика:
 *   `+`      → onShot(pid, "ball",   null, +1)   — быстрый +1
 *   `Штраф`  → onShot(pid, "foul",   null, -1)   — штрафной удар, −1 шар
 *   `Чужой`  → onShot(pid, "ball",   null, +1)   — то же что "+"
 *   `Свой`   → onShot(pid, "svoiak", null, +1)   — свояк (если profile.allowsSvoiak)
 */
@Composable
fun CounterScorer(
    pid: Long,
    profile: RuleProfile,
    currentScore: Int,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
    onDecrement: (participantId: Long) -> Unit = {},
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
            Button(
                onClick = { onShot(pid, "ball", null, +1) },
                modifier = Modifier.weight(1f),
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            OutlinedButton(
                onClick = { onShot(pid, "foul", null, -1) },
                modifier = Modifier.weight(1f),
            ) { Text("Штраф") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onShot(pid, "ball", null, +1) },
                modifier = Modifier.weight(1f),
            ) { Text("Чужой") }
            OutlinedButton(
                onClick = { onShot(pid, if (profile.allowsSvoiak) "svoiak" else "ball", null, +1) },
                modifier = Modifier.weight(1f),
            ) { Text("Свой") }
        }
    }
}
