package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ParticipantDto
import com.example.billiardtracker.domain.rules.PayoutResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutSheet(
    payout: PayoutResult,
    participants: List<ParticipantDto>,
    onDismiss: () -> Unit,
) {
    val nameById = participants.associate { it.id to it.displayName }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Итог партии", style = MaterialTheme.typography.headlineSmall)

            Divider()
            Text("Счёт", style = MaterialTheme.typography.titleSmall)
            payout.scores.forEach { (pid, score) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nameById[pid] ?: "?")
                    Text("$score", fontWeight = FontWeight.Bold)
                }
            }

            if (payout.payouts.isEmpty()) {
                Divider()
                Text("Ничья — никто никому не должен")
            } else {
                Divider()
                Text("Выплаты", style = MaterialTheme.typography.titleSmall)
                payout.payouts.forEach { p ->
                    val from = nameById[p.fromParticipantId] ?: "?"
                    val to = nameById[p.toParticipantId] ?: "?"
                    val rub = p.amountKop / 100.0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$from → $to")
                        Text("%.2f ₽".format(rub), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
