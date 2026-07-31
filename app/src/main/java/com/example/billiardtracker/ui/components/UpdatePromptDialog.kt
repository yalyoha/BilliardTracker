package com.example.billiardtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.VersionDto

@Composable
fun UpdatePromptDialog(
    latest: VersionDto,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступно обновление v${latest.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest.changelog.isNotBlank()) Text(latest.changelog)
                latest.sizeBytes?.let {
                    Text("Размер: ${it / 1024 / 1024} МБ", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Обновить") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Пропустить") }
                TextButton(onClick = onDismiss) { Text("Позже") }
            }
        },
    )
}
