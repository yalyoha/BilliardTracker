package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showPayout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.tournament?.title ?: "Турнир") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    if (ui.currentGame?.status == "finished") {
                        TextButton(onClick = { showPayout = true }) { Text("Итог") }
                    }
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

            // Scoreboard
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Счёт", style = MaterialTheme.typography.titleSmall)
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
                        Text("$name$marker", modifier = Modifier.weight(1f))
                        Text("${scores[p.id] ?: 0}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Divider()

            // Panel by role
            if (ui.isReferee) {
                val cg = ui.currentGame
                if (cg == null || cg.status == "finished") {
                    Column(Modifier.padding(16.dp)) {
                        Button(onClick = viewModel::startGame) { Text("Начать партию") }
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

        if (showPayout) {
            val payout = viewModel.payout
            val tt = ui.tournament
            if (payout != null && tt != null) {
                PayoutSheet(
                    payout = payout,
                    tournament = tt,
                    participants = tt.participants,
                    currentUserId = ui.myUserId,
                    myLocalName = ui.myLocalName,
                    gameId = ui.currentGame?.id,
                    onDonate = { body -> viewModel.donate(body) },
                    onDismiss = { showPayout = false },
                )
            }
        }
    }
}
