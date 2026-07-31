package com.example.billiardtracker.ui.screens.pick

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.contacts.ContactsReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickParticipantsScreen(
    viewModel: PickParticipantsViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onPermissionGranted(ContactsReader(ctx.contentResolver))
        else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (already) viewModel.onPermissionGranted(ContactsReader(ctx.contentResolver))
        else permLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Участники") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    Button(
                        onClick = { viewModel.commit(onNext) },
                        enabled = ui.selected.isNotEmpty() || ui.guests.isNotEmpty(),
                    ) {
                        Text("Далее")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Guests block
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Гости (без контакта в телефоне)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = ui.guestNameDraft,
                            onValueChange = viewModel::setGuestName,
                            label = { Text("Имя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = ui.guestPhoneDraft,
                            onValueChange = viewModel::setGuestPhone,
                            label = { Text("Телефон (необязательно)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::addGuest,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Добавить гостя")
                        }
                        if (ui.guests.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Добавленные (${ui.guests.size})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            ui.guests.forEachIndexed { i, g ->
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                g.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (g.phone != null) {
                                                Text(
                                                    g.phone,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                        TextButton(onClick = { viewModel.removeGuest(i) }) {
                                            Text("Удалить")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (ui.permissionDenied) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Доступ к контактам не выдан. Добавь гостей вручную выше или разреши в настройках.",
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = {
                                val i = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${ctx.packageName}"),
                                )
                                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(i)
                            }) { Text("Открыть настройки приложения") }
                        }
                    }
                }
            } else if (!ui.contactsLoaded) {
                item { Text("Загрузка контактов…") }
            } else if (ui.contacts.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Контактов не найдено на устройстве.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Добавь гостя вручную через форму выше.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(ui.contacts) { i, c ->
                    val checked = i in ui.selected
                    ElevatedCard(
                        onClick = { viewModel.toggle(i) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { viewModel.toggle(i) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium)
                                Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
