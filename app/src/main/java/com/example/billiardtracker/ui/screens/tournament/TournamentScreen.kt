package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.ParticipantDto

/**
 * Prefer the local user's own name over the server-side displayName when the
 * participant is the current user. Backend uses `phone` as fallback when the
 * user has no server-side name set — but the client already knows the friendly
 * name from UserPrefs, so show that instead of a bare phone number.
 */
internal fun ParticipantDto.effectiveName(currentUserId: Long, myLocalName: String?): String {
    return if (userId != null && userId == currentUserId && !myLocalName.isNullOrBlank()) {
        myLocalName
    } else {
        displayName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    viewModel: TournamentViewModel,
    onBack: () -> Unit,
    onOpenPayout: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Отслеживаем смену маркёра — показываем всем остальным toast "X стал маркёром".
    // SSE не идёт офлайн, поэтому событие увидят только online-участники — норм.
    var lastReferee by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(ui.tournament?.refereeUserId) {
        val current = ui.tournament?.refereeUserId
        val prev = lastReferee
        if (prev != null && current != null && current != prev && current != ui.myUserId) {
            val name = ui.tournament?.participants
                ?.firstOrNull { it.userId == current }
                ?.effectiveName(ui.myUserId, ui.myLocalName) ?: "Кто-то"
            android.widget.Toast.makeText(ctx, "$name стал маркёром", android.widget.Toast.LENGTH_SHORT).show()
        }
        lastReferee = current
    }

    Scaffold(
        topBar = {
            com.example.billiardtracker.ui.components.BilliardTopBar(
                title = { Text(ui.tournament?.title ?: "Встреча") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    // Только не-маркёр видит "Стать маркёром" — любой участник
                    // может взять роль. Кнопки "Передать" убрали: role-transfer
                    // теперь через claim от нового маркёра (online only).
                    if (!ui.isReferee && ui.tournament?.status == "active") {
                        TextButton(onClick = viewModel::claimReferee) { Text("Стать маркёром") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            if (ui.loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
                return@Column
            }
            val t = ui.tournament ?: return@Column

            // Wins per participant (winner_participant_id of finished games).
            val winsByPid: Map<Long, Int> = ui.games
                .mapNotNull { it.winnerParticipantId }
                .groupingBy { it }
                .eachCount()
            val target = t.winsRequired
            val champion = target?.let { n ->
                winsByPid.entries.firstOrNull { it.value >= n }?.key?.let { pid ->
                    t.participants.firstOrNull { it.id == pid }
                }
            }

            if (champion != null && target != null) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "🏆 Победитель: ${champion.effectiveName(ui.myUserId, ui.myLocalName)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "Достиг $target побед.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                if (ui.isReferee && t.status == "active") {
                    Button(
                        onClick = { viewModel.closeTournament(onOpenPayout) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) { Text("Закрыть встречу") }
                } else if (t.status == "finished") {
                    Button(
                        onClick = onOpenPayout,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) { Text("Итоги") }
                }
            }

            // Scoreboard текущей партии + счёт побед по турниру.
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (target != null) "Играем до $target побед" else "Счёт партии",
                    style = MaterialTheme.typography.titleSmall,
                )
                val scores =
                    ui.currentGame?.scores?.associate { it.participantId to it.points } ?: emptyMap()
                t.participants.forEach { p ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val marker =
                            if (t.refereeUserId != null && p.userId == t.refereeUserId) " 🎩" else ""
                        val name = p.effectiveName(ui.myUserId, ui.myLocalName)
                        val wins = winsByPid[p.id] ?: 0
                        val winsSuffix = target?.let { " · $wins/$it побед" } ?: " · $wins побед"
                        Text(
                            "$name$marker$winsSuffix",
                            modifier = Modifier.weight(1f),
                        )
                        Text("${scores[p.id] ?: 0}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider()

            // История партий.
            val finishedGames = ui.games.filter { it.status == "finished" }
            if (finishedGames.isNotEmpty()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Партии", style = MaterialTheme.typography.titleSmall)
                    finishedGames.forEach { g ->
                        FinishedGameRow(
                            game = g,
                            participants = t.participants,
                            currentUserId = ui.myUserId,
                            myLocalName = ui.myLocalName,
                        )
                    }
                }
                Divider()
            }

            // Panel by role — MatchLayout для всех, наблюдатели получают readonly-плитки.
            val cg = ui.currentGame
            val profile = com.example.billiardtracker.domain.rules.RuleProfile.forType(
                com.example.billiardtracker.domain.rules.GameType.entries
                    .firstOrNull { it.ruleFileSlug == t.gameType } ?: com.example.billiardtracker.domain.rules.GameType.FREE_PYRAMID
            )
            if (cg == null || cg.status == "finished") {
                if (ui.isReferee && t.status == "active") {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = viewModel::startGame,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Начать партию") }
                    }
                } else if (!ui.isReferee) {
                    val refereeName = t.participants
                        .firstOrNull { it.userId == t.refereeUserId }
                        ?.effectiveName(ui.myUserId, ui.myLocalName)
                        ?: "?"
                    Text(
                        "Партия не идёт. Маркёр — $refereeName.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val pottedBalls = ui.currentGameShots
                    .filter { it.kind == "ball" && it.ballNumber != null }
                    .mapNotNull { it.ballNumber }
                    .toSet()
                val scoresByPid: Map<Long, Int> = cg.scores.associate { it.participantId to it.points }
                var sheetPid by remember { mutableStateOf<Long?>(null) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                ) {
                    com.example.billiardtracker.ui.screens.tournament.scorers.MatchLayout(
                        participants = t.participants,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { p ->
                        com.example.billiardtracker.ui.screens.tournament.scorers.ScoreTile(
                            name = p.effectiveName(ui.myUserId, ui.myLocalName),
                            isReferee = t.refereeUserId != null && p.userId == t.refereeUserId,
                            score = scoresByPid[p.id] ?: 0,
                        ) {
                            if (!ui.isReferee) {
                                Text(
                                    "наблюдатель",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else when (profile.scorerKind) {
                                com.example.billiardtracker.domain.rules.ScorerKind.Counter ->
                                    com.example.billiardtracker.ui.screens.tournament.scorers.CounterScorer(
                                        pid = p.id,
                                        profile = profile,
                                        currentScore = scoresByPid[p.id] ?: 0,
                                        onShot = viewModel::addShot,
                                        onDecrement = viewModel::decrementScore,
                                    )
                                com.example.billiardtracker.domain.rules.ScorerKind.Lives ->
                                    com.example.billiardtracker.ui.screens.tournament.scorers.LivesScorer(
                                        pid = p.id,
                                        shots = ui.currentGameShots,
                                        initialLives = 3,
                                        onLifeLost = { pid ->
                                            viewModel.addShot(pid, "life", null, -1)
                                        },
                                    )
                                com.example.billiardtracker.domain.rules.ScorerKind.NumberedBallGrid,
                                com.example.billiardtracker.domain.rules.ScorerKind.Balance,
                                com.example.billiardtracker.domain.rules.ScorerKind.Fishki ->
                                    com.example.billiardtracker.ui.screens.tournament.scorers.NumberedBallGridTile(
                                        pid = p.id,
                                        onSelect = { sheetPid = it },
                                    )
                            }
                        }
                    }
                }
                if (ui.isReferee) {
                    val targetHint = buildString {
                        when {
                            profile.winTargetPoints != null -> append("до ${profile.winTargetPoints} очков")
                            profile.winTargetBalls != null -> append("до ${profile.winTargetBalls} шаров")
                            else -> append("баланс / жизни")
                        }
                        t.participants.forEach { p ->
                            val s = scoresByPid[p.id] ?: 0
                            append(" · ${p.effectiveName(ui.myUserId, ui.myLocalName)} $s")
                        }
                    }
                    val autoWinner = t.participants
                        .maxByOrNull { scoresByPid[it.id] ?: 0 }
                    com.example.billiardtracker.ui.screens.tournament.scorers.MatchBottomBar(
                        targetHint = targetHint,
                        winnerName = autoWinner?.effectiveName(ui.myUserId, ui.myLocalName),
                        winnerScore = autoWinner?.let { scoresByPid[it.id] ?: 0 },
                        onUndo = viewModel::undoLastShot,
                        onFinish = { viewModel.finishGame() },
                    )
                }
                sheetPid?.let { pid ->
                    val name = t.participants.firstOrNull { it.id == pid }
                        ?.effectiveName(ui.myUserId, ui.myLocalName) ?: ""
                    com.example.billiardtracker.ui.screens.tournament.scorers.NumberedBallGridSheet(
                        selectedPid = pid,
                        selectedName = name,
                        pottedBalls = pottedBalls,
                        onDismiss = { sheetPid = null },
                        onShot = viewModel::addShot,
                    )
                }
            }
        }

    }
}

@Composable
private fun FinishedGameRow(
    game: GameDto,
    participants: List<ParticipantDto>,
    currentUserId: Long,
    myLocalName: String?,
) {
    val winner = participants.firstOrNull { it.id == game.winnerParticipantId }
    val scoresText = game.scores
        .sortedByDescending { it.points }
        .joinToString(" · ") { s ->
            val name = participants.firstOrNull { it.id == s.participantId }
                ?.effectiveName(currentUserId, myLocalName) ?: "?"
            "$name ${s.points}"
        }
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Партия ${game.orderIndex}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            winner?.let {
                Text(
                    "🏆 ${it.effectiveName(currentUserId, myLocalName)}",
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
