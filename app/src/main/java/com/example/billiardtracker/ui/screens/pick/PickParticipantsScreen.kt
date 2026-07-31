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
                        ui.guests.forEach { g ->
                            Text(
                                "• ${g.displayName}${if (g.phone != null) " (${g.phone})" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (ui.permissionDenied) {
                item {
                    Text(
                        "Доступ к контактам не выдан. Добавьте гостей вручную ↑",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else if (!ui.contactsLoaded) {
                item { Text("Загрузка контактов…") }
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
