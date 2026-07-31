package com.example.billiardtracker.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val list by viewModel.tournaments.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Статистика") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Всего турниров: ${list.size}", style = MaterialTheme.typography.titleMedium)
            val active = list.count { it.status == "active" }
            val finished = list.count { it.status == "finished" }
            Text("Активные: $active  •  Завершённые: $finished")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Детальная статистика (W/L, средний счёт) появится в следующей версии.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
