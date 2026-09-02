package com.example.billiardtracker.ui.screens.tournament

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.remote.dto.CreateDonationBody
import com.example.billiardtracker.ui.components.BilliardTopBar
import kotlinx.coroutines.launch

/**
 * Money formatter that keeps the ruble sign glued to the number — U+00A0
 * (non-breaking space) prevents wrapping between amount and currency, which
 * looked broken on narrow rows like "12,00" \n "₽".
 */
private fun formatRub(kop: Long): String {
    val rub = kop / 100.0
    return "%.2f ₽".format(rub)
}

private fun formatRubShort(kop: Long): String {
    val rub = kop / 100.0
    return "%.0f ₽".format(rub)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutScreen(
    viewModel: TournamentViewModel,
    onClose: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            BilliardTopBar(
                title = { Text("Итоги встречи") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                },
            )
        },
    ) { padding ->
        val t = ui.tournament
        val payout = viewModel.tournamentPayout
        if (t == null || payout == null) {
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                Text("Загрузка итогов…")
            }
            return@Scaffold
        }

        val participants = t.participants
        val nameById = participants.associate { it.id to it.effectiveName(ui.myUserId, ui.myLocalName) }
        val userIdByPid = participants.associate { it.id to it.userId }

        val winsByPid = ui.games.mapNotNull { it.winnerParticipantId }
            .groupingBy { it }.eachCount()
        val championPid = t.winsRequired?.let { n ->
            winsByPid.entries.firstOrNull { it.value >= n }?.key
        } ?: payout.scores.maxByOrNull { it.value }?.key
        val championName = championPid?.let { nameById[it] } ?: "?"

        val winnerUserId = championPid?.let { userIdByPid[it] }
        val netWinKop = payout.payouts.filter { it.toParticipantId == championPid }.sumOf { it.amountKop }
        val currentUserIsWinner = winnerUserId != null && winnerUserId == ui.myUserId

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(20.dp),
            ) {
                Text(
                    "🏆 Победитель: $championName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                val wins = championPid?.let { winsByPid[it] } ?: 0
                Text(
                    "$wins побед во встрече",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Победы", style = MaterialTheme.typography.titleSmall)
                    participants.forEach { p ->
                        val wins = winsByPid[p.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                nameById[p.id] ?: "?",
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$wins",
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (t.moneyPerBallKop != null) {
                val netByPid = mutableMapOf<Long, Long>()
                payout.payouts.forEach { entry ->
                    netByPid[entry.toParticipantId] =
                        (netByPid[entry.toParticipantId] ?: 0L) + entry.amountKop
                    netByPid[entry.fromParticipantId] =
                        (netByPid[entry.fromParticipantId] ?: 0L) - entry.amountKop
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Выигрыш", style = MaterialTheme.typography.titleSmall)
                        participants.forEach { p ->
                            val net = netByPid[p.id] ?: 0L
                            val color = when {
                                net > 0 -> MaterialTheme.colorScheme.primary
                                net < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val label = when {
                                net > 0 -> "+${formatRub(net)}"
                                net < 0 -> formatRub(net)
                                else -> "0 ₽"
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(nameById[p.id] ?: "?", modifier = Modifier.weight(1f))
                                Text(label, fontWeight = FontWeight.Bold, color = color, softWrap = false, maxLines = 1)
                            }
                        }
                    }
                }
            }

            val finishedGames = ui.games.filter { it.status == "finished" }
            if (finishedGames.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Партии", style = MaterialTheme.typography.titleSmall)
                        finishedGames.forEach { g ->
                            val winner = participants.firstOrNull { it.id == g.winnerParticipantId }
                            val scoresText = g.scores
                                .sortedByDescending { it.points }
                                .joinToString(" · ") { s ->
                                    val nm = nameById[s.participantId] ?: "?"
                                    "$nm ${s.points}"
                                }
                            Column {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Партия ${g.orderIndex}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    winner?.let {
                                        Text(
                                            "🏆 ${nameById[it.id] ?: "?"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                if (scoresText.isNotBlank()) {
                                    Text(
                                        scoresText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (payout.payouts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Выплаты", style = MaterialTheme.typography.titleSmall)
                        payout.payouts.forEach { p ->
                            val from = nameById[p.fromParticipantId] ?: "?"
                            val to = nameById[p.toParticipantId] ?: "?"
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "$from → $to",
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatRub(p.amountKop),
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            } else if (payout.scores.isNotEmpty() && t.moneyPerBallKop != null) {
                Text(
                    "Ничья по деньгам — никто никому не должен.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Donation-блок временно скрыт (v1.15.1) — юзер тестит с
             // друзьями, включим когда будет понимание что всё работает.
             // Раскомментировать целиком чтобы вернуть.
             /*
            if (winnerUserId != null && netWinKop > 0) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentUserIsWinner) {
                            WinnerDonationBlock(
                                netWinKop = netWinKop,
                                tournamentId = t.id,
                                gameId = ui.currentGame?.id,
                                winnerUserId = winnerUserId,
                                onDonate = { body -> scope.launch { viewModel.donate(body) } },
                            )
                        } else {
                            val winnerName = nameById[championPid] ?: "победителю"
                            ForwardDonationBlock(
                                winnerName = winnerName,
                                tournamentId = t.id,
                                netWinKop = netWinKop,
                                winnerUserId = winnerUserId,
                                context = ctx,
                            )
                        }
                    }
                }
            }
             */
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

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Твой чистый выигрыш:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatRub(netWinKop),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            maxLines = 1,
        )
    }
    Text("Поддержишь разработчика?", style = MaterialTheme.typography.bodyMedium)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(3, 5, 10).forEach { pct ->
            val chipAmountKop = netWinKop * pct / 100L
            FilterChip(
                selected = selectedPercent == pct,
                onClick = { selectedPercent = pct },
                label = {
                    Column {
                        Text("$pct%")
                        Text(
                            formatRubShort(chipAmountKop),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.primary,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedPercent == pct,
                    borderColor = MaterialTheme.colorScheme.primary,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp,
                ),
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
            "Спасибо! Заявка сохранена.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Поддержать") }
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
        "Отправь $winnerName ссылку — пусть поддержит разработчика.",
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
                    "Поздравляю с победой! Победил на ${formatRub(netWinKop)}. " +
                        "Если хочешь — поддержи разработчика приложения: $url",
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Отправить"))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Отправить ссылку") }
}
