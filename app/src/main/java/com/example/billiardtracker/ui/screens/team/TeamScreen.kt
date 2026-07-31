package com.example.billiardtracker.ui.screens.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(viewModel: TeamViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showOnboarding by remember(ui.needOnboarding) { mutableStateOf(ui.needOnboarding) }

    Scaffold(topBar = { TopAppBar(title = { Text("Команда") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Набор игроков для игры. Живёт только на этом устройстве.",
                style = MaterialTheme.typography.bodySmall,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Добавить игрока", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = ui.nameDraft,
                        onValueChange = viewModel::setNameDraft,
                        label = { Text("Имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PhoneMaskInput(
                        value = ui.phoneDraft,
                        onChange = viewModel::setPhoneDraft,
                        label = "Телефон (необязательно)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::addPlayer, modifier = Modifier.fillMaxWidth()) {
                        Text("Добавить")
                    }
                }
            }

            if (ui.players.isEmpty()) {
                Text(
                    "Пока никого — добавь друзей выше или через контакты (следующая версия).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(ui.players) { i, p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                    if (p.phone != null) {
                                        Text(
                                            formatPhone(p.phone),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                TextButton(onClick = { viewModel.removePlayer(i) }) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            onSave = { phone, name ->
                viewModel.completeOnboarding(phone, name)
                showOnboarding = false
            },
            onDismiss = { showOnboarding = false },
        )
    }
}

@Composable
private fun OnboardingDialog(
    onSave: (phone: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заполни профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Введи свой номер и имя. Сохраним локально, без SMS.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Твоё имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PhoneMaskInput(
                    value = phone,
                    onChange = { phone = it },
                    label = "Твой телефон",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(phone, name) },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Позже") } },
    )
}
