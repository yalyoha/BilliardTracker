package com.example.billiardtracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.util.ApkInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Версия: v${ui.currentVersionName}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Обновлять автоматически", style = MaterialTheme.typography.titleSmall)
                            Text("Проверять новые версии при запуске", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoCheck, onCheckedChange = viewModel::setAutoCheck)
                    }
                    Button(
                        onClick = { viewModel.checkNow(); showDialog = true },
                        enabled = !ui.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (ui.checking) "Проверяем…" else "Проверить обновления") }
                    if (ui.message != null) Text(ui.message!!, style = MaterialTheme.typography.bodySmall)
                }
            }

        }

        val latest = ui.latest
        if (showDialog && latest != null && latest.versionCode > ui.currentVersionCode) {
            UpdatePromptDialog(
                latest = latest,
                onUpdate = {
                    showDialog = false
                    ApkInstaller.downloadAndInstall(ctx, latest.apkUrl, latest.versionName)
                },
                onDismiss = { showDialog = false },
                onSkip = { viewModel.skip(latest.versionCode); showDialog = false },
            )
        }
    }
}
