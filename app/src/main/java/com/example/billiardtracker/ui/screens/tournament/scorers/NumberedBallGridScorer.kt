package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BallWhite = Color(0xFFF5EFD9)
private val BallGrey = Color(0xFF6B6B6B)
private val BallText = Color(0xFF1A1A1A)

/**
 * Плитка для Group A (Классика/61/71/Колхоз-fallback/Фишки-fallback). Компактна
 * (тап-таргет — весь Box плитки). При тапе — [onSelect] раскрывает shared
 * bottom-sheet со всеми шарами + действиями. Родитель (TournamentScreen)
 * держит `selectedPid` и `sheetOpen` state, потому что bottom-sheet у нас
 * общий на все плитки.
 */
@Composable
fun NumberedBallGridTile(
    pid: Long,
    onSelect: (pid: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable { onSelect(pid) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Тап для ввода счёта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shared bottom-sheet со всеми шарами + Свояк/Штраф/За борт. `selectedPid`
 * приходит от родителя (выбор был через тап на плитку). Ball-tap отправляет
 * shot на selectedPid и закрывает sheet. `pottedBalls` — множество забитых
 * шаров в текущей партии (собирается TournamentScreen'ом).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberedBallGridSheet(
    selectedPid: Long,
    selectedName: String,
    pottedBalls: Set<Int>,
    onDismiss: () -> Unit,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Забитый шар — $selectedName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            for (row in 0..2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (col in 0..4) {
                        val ball = row * 5 + col + 1
                        val potted = ball in pottedBalls
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (potted) BallGrey else BallWhite)
                                .clickable(enabled = !potted) {
                                    onShot(selectedPid, "ball", ball, 1)
                                    close()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$ball",
                                color = BallText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onShot(selectedPid, "svoiak", null, 1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("Свояк") }
                OutlinedButton(
                    onClick = { onShot(selectedPid, "foul", null, -1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("Штраф") }
                OutlinedButton(
                    onClick = { onShot(selectedPid, "ball_out", null, -1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("За борт") }
            }
            Button(
                onClick = { close() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("Закрыть") }
        }
    }
}
