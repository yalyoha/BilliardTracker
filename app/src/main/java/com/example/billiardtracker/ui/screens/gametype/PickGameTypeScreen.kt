package com.example.billiardtracker.ui.screens.gametype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.domain.rules.RuleProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickGameTypeScreen(
    viewModel: PickGameTypeViewModel,
    onBack: () -> Unit,
    onOpenRules: (String) -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Выбор игры") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
        ) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(viewModel.gameTypes, key = { it.name }) { gt ->
                GameTypeCard(gt, onClick = { viewModel.choose(gt, onNext) }, onRules = { onOpenRules(gt.ruleFileSlug) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTypeCard(gt: GameType, onClick: () -> Unit, onRules: () -> Unit) {
    val profile = RuleProfile.forType(gt)
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(gt.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val brief = buildString {
                append(profile.ballValues.size)
                append(" шаров, ")
                when {
                    profile.winTargetPoints != null -> append("до ${profile.winTargetPoints} очков")
                    profile.winTargetBalls != null -> append("до ${profile.winTargetBalls} шаров")
                    else -> append("до конца шаров")
                }
                if (profile.moneyPlayable) append(" • можно на шар")
                if (profile.tabbedBallsAllowed) append(" • с кеглями")
            }
            Text(brief, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRules) { Text("Правила →") }
        }
    }
}
