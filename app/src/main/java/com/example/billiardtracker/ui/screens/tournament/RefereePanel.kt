package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billiardtracker.data.remote.dto.ParticipantDto

private val BallWhite = Color(0xFFF5EFD9)   // BallCream — незабитый
private val BallGrey = Color(0xFF6B6B6B)    // забитый — приглушённый серый
private val BallText = Color(0xFF1A1A1A)    // цифра — тёмная

@Composable
fun RefereePanel(
    participants: List<ParticipantDto>,
    currentUserId: Long,
    myLocalName: String?,
    pottedBalls: Set<Int>,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
    onUndo: () -> Unit,
    onFinish: (winnerPid: Long?) -> Unit,
) {
    val selectedPidState = remember { mutableStateOf(participants.firstOrNull()?.id ?: 0L) }
    val selectedPid = selectedPidState.value
    val goldBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Участники турнира", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            participants.forEach { p ->
                val isActive = p.id == selectedPid
                if (isActive) {
                    Button(
                        onClick = { selectedPidState.value = p.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) { CenterText(p.effectiveName(currentUserId, myLocalName)) }
                } else {
                    OutlinedButton(
                        onClick = { selectedPidState.value = p.id },
                        border = goldBorder,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { CenterText(p.effectiveName(currentUserId, myLocalName)) }
                }
            }
        }

        Divider()
        Text("Забитый шар", style = MaterialTheme.typography.titleSmall)
        for (row in 0..2) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0..4) {
                    val ball = row * 5 + col + 1
                    val potted = ball in pottedBalls
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (potted) BallGrey else BallWhite)
                            .clickable(enabled = !potted) {
                                onShot(selectedPid, "ball", ball, 1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$ball",
                            color = BallText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onShot(selectedPid, "svoiak", null, 1) },
                border = goldBorder,
                modifier = Modifier.weight(1f),
            ) { CenterText("Свояк") }
            OutlinedButton(
                onClick = { onShot(selectedPid, "foul", null, -1) },
                border = goldBorder,
                modifier = Modifier.weight(1f),
            ) { CenterText("Штраф") }
            OutlinedButton(
                onClick = { onShot(selectedPid, "ball_out", null, -1) },
                border = goldBorder,
                modifier = Modifier.weight(1f),
            ) { CenterText("За борт") }
        }

        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onUndo,
                border = goldBorder,
                modifier = Modifier.weight(1f),
            ) { CenterText("Отменить последний") }
            Button(
                onClick = { onFinish(null) },
                modifier = Modifier.weight(1f),
            ) { CenterText("Партия окончена") }
        }
    }
}

/** Однострочный/многострочный текст, отцентрированный в кнопке. */
@Composable
private fun CenterText(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
