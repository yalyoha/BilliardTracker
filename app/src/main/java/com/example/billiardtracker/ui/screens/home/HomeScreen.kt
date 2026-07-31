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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.local.entity.TournamentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewTournament: () -> Unit,
    onOpenTournament: (Long) -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddClub: () -> Unit,
) {
    val list by viewModel.tournaments.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои турниры") },
                actions = {
                    TextButton(onClick = onAddClub) { Text("+ Клуб") }
                    TextButton(onClick = onOpenRules) { Text("Правила") }
                    TextButton(onClick = onOpenSettings) { Text("⚙") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTournament,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Новый турнир") },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Пока ни одного турнира", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нажми «Новый турнир», чтобы начать",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    TournamentCard(t, onClick = { onOpenTournament(t.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TournamentCard(t: TournamentEntity, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                t.title ?: "Без названия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(displayGameType(t.gameType), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            AssistChip(
                onClick = {},
                label = { Text(if (t.status == "active") "Идёт" else "Закончен") },
            )
        }
    }
}

private fun displayGameType(slug: String): String = when (slug) {
    "svobodnaya-piramida" -> "Свободная пирамида"
    "kombinirovannaya-piramida" -> "Комбинированная"
    "dinamichnaya-piramida" -> "Динамичная"
    "klassicheskaya-piramida" -> "Классическая (71 очко)"
    "svobodnaya-s-prodolzheniem" -> "Свободная с продолжением"
    "malaya-russkaya-partiya" -> "Малая русская партия"
    "bolshaya-russkaya-partiya" -> "Большая русская партия"
    "alagyor" -> "Алагёр"
    "yaroslavskaya-piramida" -> "Ярославская"
    "kolkhoz" -> "Колхоз"
    "fishki" -> "Фишки"
    "odin-karman" -> "Один карман"
    "grosh" -> "Грош"
    "evropeyskaya-piramida" -> "Европейская пирамида"
    else -> slug
}
