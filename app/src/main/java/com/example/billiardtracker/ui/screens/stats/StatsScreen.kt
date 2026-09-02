package com.example.billiardtracker.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.domain.rules.GameType
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenPayout: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val finished by viewModel.finishedTournaments.collectAsStateWithLifecycle()
    val localStats by viewModel.localStats.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Всего", "По дисциплине", "По сопернику")

    Scaffold(topBar = { BilliardTopBar(title = { Text("Статистика") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> TabTotal(ui = ui, localStats = localStats, finished = finished, onOpenPayout = onOpenPayout)
                1 -> TabByDiscipline(localStats = localStats)
                2 -> TabByOpponent(localStats = localStats)
            }
        }
    }
}

@Composable
private fun TabTotal(
    ui: StatsUiState,
    localStats: LocalStats?,
    finished: List<com.example.billiardtracker.data.local.entity.TournamentEntity>,
    onOpenPayout: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val s = ui.stats
        if (ui.loading && s == null && localStats == null) {
            Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
        } else {
            if (s != null) {
                StatCard {
                    StatRow("Встреч всего", "${s.tournaments.total}")
                    StatRow("Активных", "${s.tournaments.active}")
                    StatRow("Завершённых", "${s.tournaments.finished}")
                }
            }
            val gamesPlayed = localStats?.gamesPlayed ?: s?.games?.played ?: 0
            val gamesWon = localStats?.gamesWon ?: s?.games?.won ?: 0
            StatCard {
                Text("Партии", style = MaterialTheme.typography.titleSmall)
                StatRow("Сыграно", "$gamesPlayed")
                StatRow("Побед", "$gamesWon")
                if (gamesPlayed > 0) {
                    StatRow("Процент побед", "%.1f%%".format(gamesWon.toDouble() / gamesPlayed * 100))
                }
            }
            if (localStats != null) {
                BallStatCard(
                    totalBalls = localStats.totalBalls,
                    foreignBalls = localStats.foreignBalls,
                    ownBalls = localStats.ownBalls,
                    fouls = localStats.fouls,
                    gamesPlayed = gamesPlayed,
                )
            }
        }

        if (finished.isNotEmpty()) {
            Text(
                "Сыгранные встречи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            finished.forEach { t ->
                Card(
                    onClick = { onOpenPayout(t.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            t.title ?: "Без названия",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            displayGameType(t.gameType),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabByDiscipline(localStats: LocalStats?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (localStats == null) {
            Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (localStats.byDiscipline.isEmpty()) {
            Text("Нет завершённых встреч", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        localStats.byDiscipline.forEach { d ->
            DisciplineCard(
                name = d.displayName,
                meetings = d.meetings,
                gamesPlayed = d.gamesPlayed,
                gamesWon = d.gamesWon,
                foreignBalls = d.foreignBalls,
                ownBalls = d.ownBalls,
                fouls = d.fouls,
            )
        }
    }
}

@Composable
private fun TabByOpponent(localStats: LocalStats?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (localStats == null) {
            Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (localStats.byOpponent.isEmpty()) {
            Text("Нет завершённых встреч", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        localStats.byOpponent.forEach { o ->
            StatCard {
                Text(o.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                StatRow("Совместных встреч", "${o.meetings}")
                StatRow("Партий сыграно", "${o.gamesPlayed}")
                StatRow("Партий выиграно", "${o.gamesWon}")
                if (o.gamesPlayed > 0) {
                    StatRow("Процент побед", "%.1f%%".format(o.gamesWon.toDouble() / o.gamesPlayed * 100))
                }
                BallStatRows(
                    totalBalls = o.foreignBalls + o.ownBalls,
                    foreignBalls = o.foreignBalls,
                    ownBalls = o.ownBalls,
                    fouls = o.fouls,
                    gamesPlayed = o.gamesPlayed,
                )
            }
        }
    }
}

@Composable
private fun BallStatCard(
    totalBalls: Int,
    foreignBalls: Int,
    ownBalls: Int,
    fouls: Int,
    gamesPlayed: Int,
) {
    StatCard {
        Text("Счёт", style = MaterialTheme.typography.titleSmall)
        BallStatRows(totalBalls, foreignBalls, ownBalls, fouls, gamesPlayed)
    }
}

@Composable
private fun BallStatRows(
    totalBalls: Int,
    foreignBalls: Int,
    ownBalls: Int,
    fouls: Int,
    gamesPlayed: Int,
) {
    StatRow("Забито шаров", "$totalBalls")
    StatRow("  Чужой", "$foreignBalls")
    StatRow("  Свой (свояк)", "$ownBalls")
    StatRow("Штрафов", "$fouls")
    if (gamesPlayed > 0) {
        StatRow("Шаров за партию", "%.1f".format(totalBalls.toDouble() / gamesPlayed))
    }
}

@Composable
private fun DisciplineCard(
    name: String,
    meetings: Int,
    gamesPlayed: Int,
    gamesWon: Int,
    foreignBalls: Int,
    ownBalls: Int,
    fouls: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatRow("Встреч", "$meetings")
            StatRow("Партий сыграно", "$gamesPlayed")
            StatRow("Партий выиграно", "$gamesWon")
            if (gamesPlayed > 0) {
                StatRow("Процент побед", "%.1f%%".format(gamesWon.toDouble() / gamesPlayed * 100))
            }
            BallStatRows(
                totalBalls = foreignBalls + ownBalls,
                foreignBalls = foreignBalls,
                ownBalls = ownBalls,
                fouls = fouls,
                gamesPlayed = gamesPlayed,
            )
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

private fun displayGameType(slug: String): String =
    GameType.entries.firstOrNull { it.ruleFileSlug == slug }?.displayName ?: slug
