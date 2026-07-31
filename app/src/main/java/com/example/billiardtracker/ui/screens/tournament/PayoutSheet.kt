package com.example.billiardtracker.ui.screens.tournament

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.CreateDonationBody
import com.example.billiardtracker.data.remote.dto.DonationDto
import com.example.billiardtracker.data.remote.dto.ParticipantDto
import com.example.billiardtracker.data.remote.dto.TournamentDto
import com.example.billiardtracker.domain.rules.PayoutResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutSheet(
    payout: PayoutResult,
    tournament: TournamentDto,
    participants: List<ParticipantDto>,
    currentUserId: Long,
    gameId: Long?,
    onDonate: suspend (CreateDonationBody) -> Result<DonationDto>,
    onDismiss: () -> Unit,
) {
    val nameById = participants.associate { it.id to it.displayName }
    val userIdByPid = participants.associate { it.id to it.userId }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Determine winner: prefer top payout receiver; else max score
    val topPayoutToPid = payout.payouts.maxByOrNull { it.amountKop }?.toParticipantId
    val winnerPid = topPayoutToPid ?: payout.scores.maxByOrNull { it.value }?.key
    val winnerUserId = winnerPid?.let { userIdByPid[it] }
    val netWinKop = payout.payouts.filter { it.toParticipantId == winnerPid }.sumOf { it.amountKop }

    val currentUserIsWinner = winnerUserId != null && winnerUserId == currentUserId

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

            // Donation block: only shown when winner is known and net win is positive
            if (winnerUserId != null && netWinKop > 0) {
                Divider()
                if (currentUserIsWinner) {
                    WinnerDonationBlock(
                        netWinKop = netWinKop,
                        tournamentId = tournament.id,
                        gameId = gameId,
                        winnerUserId = winnerUserId,
                        onDonate = { body -> scope.launch { onDonate(body) } },
                    )
                } else {
                    val winnerName = nameById[winnerPid] ?: "победителю"
                    ForwardDonationBlock(
                        winnerName = winnerName,
                        tournamentId = tournament.id,
                        netWinKop = netWinKop,
                        winnerUserId = winnerUserId,
                        context = ctx,
                    )
                }
            }
        }
    }
}

@Composable
private fun WinnerDonationBlock(
    netWinKop: Long,
    tournamentId: Long,
    gameId: Long?,
    winnerUserId: Long,
    onDonate: (CreateDonationBody) -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var selectedPercent by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }

    Text(
        "Твой чистый выигрыш: %.2f ₽".format(netWinKop / 100.0),
        style = MaterialTheme.typography.titleMedium,
    )
    Text("Поддержишь разработчика?", style = MaterialTheme.typography.bodyMedium)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(3, 5, 10, 15).forEach { pct ->
            val amountRub = (netWinKop * pct / 100) / 100.0
            FilterChip(
                selected = selectedPercent == pct,
                onClick = { selectedPercent = pct },
                label = {
                    Column {
                        Text("$pct%")
                        Text("%.0f₽".format(amountRub), style = MaterialTheme.typography.bodySmall)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    OutlinedTextField(
        value = comment,
        onValueChange = { comment = it },
        label = { Text("Пожелания / замечания (необязательно)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    if (submitted) {
        Text(
            "Спасибо! Заявка сохранена. Ссылка на оплату придёт по мере готовности приёма платежей.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    val pct = selectedPercent ?: return@Button
                    val amount = netWinKop * pct / 100L
                    onDonate(
                        CreateDonationBody(
                            tournamentId = tournamentId,
                            gameId = gameId,
                            amountKop = amount,
                            percent = pct,
                            comment = comment.takeIf { it.isNotBlank() },
                            winnerUserId = winnerUserId,
                        )
                    )
                    submitted = true
                },
                enabled = selectedPercent != null,
                modifier = Modifier.weight(1f),
            ) { Text("Поддержать") }
        }
    }
}

@Composable
private fun ForwardDonationBlock(
    winnerName: String,
    tournamentId: Long,
    netWinKop: Long,
    winnerUserId: Long,
    context: Context,
) {
    Text("Победил $winnerName", style = MaterialTheme.typography.titleSmall)
    Text(
        "Ты не победитель, но ввёл счёт — можешь отправить $winnerName ссылку на пожертвование разработчику.",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = {
            val url = "https://billiardtracker.alekseylosev.ru/donate?" +
                "tournamentId=$tournamentId&amount=$netWinKop&winnerPid=$winnerUserId"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Поздравляю с победой! Победил на ${netWinKop / 100} ₽. " +
                        "Если хочешь — поддержи разработчика приложения: $url",
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Отправить"))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Отправить $winnerName ссылку на донат") }
}
