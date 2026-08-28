package com.example.billiardtracker.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.local.entity.TournamentEntity
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickGameType: (GameType) -> Unit,
    onOpenTournament: (Long) -> Unit,
) {
    val enabled by viewModel.enabledGameSlugs.collectAsStateWithLifecycle()
    val active by viewModel.activeTournaments.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BilliardTopBar(title = { Text("Новая встреча") }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val enabledTypes = GameType.entries.filter { it.ruleFileSlug in enabled }
            items(enabledTypes, key = { it.name }) { gt ->
                Button(
                    onClick = { onPickGameType(gt) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(gt.displayName) }
            }

            if (active.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Идут сейчас",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(active, key = { it.id }) { t ->
                    ActiveTournamentCard(t, onClick = { onOpenTournament(t.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveTournamentCard(t: TournamentEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                t.title ?: "Без названия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(displayGameType(t.gameType), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun displayGameType(slug: String): String =
    GameType.entries.firstOrNull { it.ruleFileSlug == slug }?.displayName ?: slug
