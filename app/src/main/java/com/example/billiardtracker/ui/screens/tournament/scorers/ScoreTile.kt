package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Общий контейнер плитки игрока в MatchLayout: имя (+ 🎩 если маркёр), крупный
 * счёт, slot под scorer. Заполняет доступное пространство от родителя
 * (`fillMaxSize` — потому что MatchLayout уже раздал weight каждому tile).
 */
@Composable
fun ScoreTile(
    name: String,
    isReferee: Boolean,
    score: Int,
    modifier: Modifier = Modifier,
    scorer: @Composable () -> Unit,
) {
    val badge = if (isReferee) " 🎩" else ""
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$name$badge",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.fillMaxSize()) { scorer() }
    }
}
