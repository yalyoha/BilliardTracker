package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    viewModel: TournamentViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
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
        Column(Modifier.padding(padding).fillMaxSize()) {
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
                        Text("${p.displayName}$marker", modifier = Modifier.weight(1f))
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
                    RefereePanel(
                        participants = t.participants,
                        onShot = viewModel::addShot,
                        onUndo = viewModel::undoLastShot,
                        onFinish = viewModel::finishGame,
                    )
                }
            } else {
                val refereeName =
                    t.participants.firstOrNull { it.userId == t.refereeUserId }?.displayName ?: "?"
                ObserverPanel(refereeName = refereeName)
            }
        }
    }
}
