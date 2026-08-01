package com.example.billiardtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.VersionDto

sealed class UpdateStage {
    data object Idle : UpdateStage()
    data class Downloading(val progress: Float) : UpdateStage()
    data class Error(val message: String) : UpdateStage()
}

@Composable
fun UpdatePromptDialog(
    latest: VersionDto,
    stage: UpdateStage,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    val downloading = stage is UpdateStage.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Доступно обновление v${latest.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest.changelog.isNotBlank()) Text(latest.changelog)
                latest.sizeBytes?.let {
                    Text("Размер: ${it / 1024 / 1024} МБ", style = MaterialTheme.typography.bodySmall)
                }
                when (stage) {
                    is UpdateStage.Downloading -> {
                        LinearProgressIndicator(
                            progress = { stage.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Скачивание ${(stage.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateStage.Error -> {
                        Text(
                            stage.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    UpdateStage.Idle -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (stage) {
                        is UpdateStage.Downloading -> "Скачиваем…"
                        is UpdateStage.Error -> "Повторить"
                        UpdateStage.Idle -> "Обновить"
                    },
                )
            }
        },
        dismissButton = if (downloading) null else {
            @Composable {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Пропустить") }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Позже") }
                }
            }
        },
    )
}
