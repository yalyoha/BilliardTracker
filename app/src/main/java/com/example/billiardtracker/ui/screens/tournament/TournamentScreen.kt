package com.example.billiardtracker.ui.screens.tournament

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    Scaffold(
        topBar = {
            com.example.billiardtracker.ui.components.BilliardTopBar(
                title = { Text(ui.tournament?.title ?: "Турнир") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    if (!ui.isReferee) {
                        TextButton(onClick = viewModel::claimReferee) { Text("Маркёр →") }
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
                    ) { Text("Закрыть турнир") }
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

            // Panel by role
            if (ui.isReferee) {
                val cg = ui.currentGame
                if (cg == null || cg.status == "finished") {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = viewModel::startGame,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Начать партию") }
                    }
                } else {
                    val pottedBalls = ui.currentGameShots
                        .filter { it.kind == "ball" && it.ballNumber != null }
                        .mapNotNull { it.ballNumber }
                        .toSet()
                    RefereePanel(
                        participants = t.participants,
                        currentUserId = ui.myUserId,
                        myLocalName = ui.myLocalName,
                        pottedBalls = pottedBalls,
                        onShot = viewModel::addShot,
                        onUndo = viewModel::undoLastShot,
                        onFinish = viewModel::finishGame,
                    )
                }
            } else {
                val refereeName = t.participants
                    .firstOrNull { it.userId == t.refereeUserId }
                    ?.effectiveName(ui.myUserId, ui.myLocalName)
                    ?: "?"
                ObserverPanel(refereeName = refereeName)
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
