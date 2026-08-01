package com.example.billiardtracker.ui.screens.club

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.remote.dto.ClubDto
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubsAdminScreen(
    viewModel: ClubsAdminViewModel,
    onBack: () -> Unit,
    onAddClub: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var toEdit by remember { mutableStateOf<ClubDto?>(null) }
    var toDelete by remember { mutableStateOf<ClubDto?>(null) }

    Scaffold(
        topBar = {
            BilliardTopBar(
                title = { Text("Клубы / Бары") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Общий список для всех пользователей. Название бара используется в автоматическом заголовке турнира.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (ui.loading && ui.clubs.isEmpty()) {
                Text("Загрузка…")
            }
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ui.clubs.forEach { club ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(club.name, style = MaterialTheme.typography.titleSmall)
                            club.address?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "%.5f, %.5f".format(club.lat, club.lon),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { toEdit = club }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                        }
                        IconButton(onClick = { toDelete = club }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Button(onClick = onAddClub, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить клуб (по геолокации)")
            }
        }
    }

    toEdit?.let { c ->
        EditClubDialog(
            initial = c,
            onCancel = { toEdit = null },
            onSave = { name, address ->
                viewModel.rename(c.id, name, address)
                toEdit = null
            },
        )
    }
    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить клуб?") },
            text = { Text("Удалить \"${c.name}\" из общего списка? Отменить нельзя.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.delete(c.id); toDelete = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun EditClubDialog(
    initial: ClubDto,
    onCancel: () -> Unit,
    onSave: (name: String, address: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var address by remember { mutableStateOf(initial.address.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Редактировать клуб") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(200) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(500) },
                    label = { Text("Адрес") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), address.trim().ifEmpty { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        },
    )
}
