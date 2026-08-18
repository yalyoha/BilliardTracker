package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ShotDto

/** Оставшиеся жизни pid'а в текущей партии. Life-shot = `kind="life"`. */
fun livesRemaining(shots: List<ShotDto>, pid: Long, initialLives: Int): Int {
    val lost = shots.count { it.participantId == pid && it.kind == "life" }
    return (initialLives - lost).coerceAtLeast(0)
}

/** Игрок выбыл (0 жизней). */
fun isEliminated(shots: List<ShotDto>, pid: Long, initialLives: Int): Boolean =
    livesRemaining(shots, pid, initialLives) == 0

/**
 * Сетка жизней для Алагёра/Гроша. По умолчанию 3 жизни (Алагёр — «кресты»,
 * обычно 3). Тап на любую крестик = onLifeLost(pid). Игрок с 0 жизней получает
 * disabled UI + бейдж «выбыл».
 */
@Composable
fun LivesScorer(
    pid: Long,
    shots: List<ShotDto>,
    initialLives: Int = 3,
    onLifeLost: (participantId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = livesRemaining(shots, pid, initialLives)
    val eliminated = remaining == 0
    Column(
        modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (eliminated) {
            Text(
                "выбыл",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until initialLives) {
                val alive = i < remaining
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (alive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable(enabled = alive) { onLifeLost(pid) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (alive) "✕" else "·",
                        color = if (alive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
