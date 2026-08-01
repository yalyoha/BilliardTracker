package com.example.billiardtracker.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { BilliardTopBar(title = { Text("Статистика") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = ui.stats
            if (ui.loading && s == null) {
                Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
            } else if (s == null) {
                Text(
                    ui.error ?: "Нет данных",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                StatCard {
                    StatRow("Турниров всего", "${s.tournaments.total}")
                    StatRow("Активных", "${s.tournaments.active}")
                    StatRow("Завершённых", "${s.tournaments.finished}")
                }
                StatCard {
                    Text("Партии", style = MaterialTheme.typography.titleSmall)
                    StatRow("Сыграно", "${s.games.played}")
                    StatRow("Побед", "${s.games.won}")
                    StatRow("Процент побед", "%.1f%%".format(s.games.winRate))
                }
                StatCard {
                    Text("Счёт", style = MaterialTheme.typography.titleSmall)
                    StatRow("Всего забито шаров", "${s.score.totalBalls}")
                    StatRow("В среднем за партию", "%.1f".format(s.score.avgPerGame))
                }
            }
        }
    }
}

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            maxLines = 1,
        )
    }
}
