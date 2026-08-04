package com.example.billiardtracker.ui.screens.team

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.contacts.ContactsReader
import com.example.billiardtracker.ui.components.BilliardTopBar
import com.example.billiardtracker.ui.nav.Team
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(viewModel: TeamViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var teamToRename by remember { mutableStateOf<Team?>(null) }
    var teamToDelete by remember { mutableStateOf<Team?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onContactsPermissionGranted(ContactsReader(ctx.contentResolver))
        else viewModel.onContactsPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        when {
            already -> viewModel.onContactsPermissionGranted(ContactsReader(ctx.contentResolver))
            !ui.contactsRequested -> permLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(topBar = { BilliardTopBar(title = { Text("Команды") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Готовые составы для быстрого старта турниров.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (ui.teams.isEmpty()) {
                Text(
                    "Ещё нет команд. Создай первую.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ui.teams.forEach { team ->
                    TeamCard(
                        team = team,
                        isActive = team.id == ui.activeTeamId,
                        expanded = team.id == ui.expandedTeamId,
                        ui = ui,
                        onExpand = { viewModel.expandTeam(team.id) },
                        onCollapse = viewModel::collapseTeam,
                        onSetActive = { viewModel.setActive(team.id) },
                        onRename = { teamToRename = team },
                        onDelete = { teamToDelete = team },
                        onAddPlayer = { viewModel.addPlayer(team.id) },
                        onRemovePlayer = { idx -> viewModel.removePlayer(team.id, idx) },
                        onNameDraft = viewModel::setNameDraft,
                        onPhoneDraft = viewModel::setPhoneDraft,
                        onPickContact = viewModel::pickContact,
                        onRequestContacts = {
                            permLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                    )
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать команду") }
        }
    }

    if (showCreateDialog) {
        NameTeamDialog(
            title = "Новая команда",
            initial = "",
            onDismiss = { showCreateDialog = false },
            onSave = { name ->
                viewModel.createTeam(name)
                showCreateDialog = false
            },
        )
    }
    teamToRename?.let { t ->
        NameTeamDialog(
            title = "Переименовать команду",
            initial = t.name,
            onDismiss = { teamToRename = null },
            onSave = { name ->
                viewModel.renameTeam(t.id, name)
                teamToRename = null
            },
        )
    }
    teamToDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            title = { Text("Удалить команду?") },
            text = { Text("Удалить \"${t.name}\" и всех её игроков?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteTeam(t.id); teamToDelete = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { teamToDelete = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TeamCard(
    team: Team,
    isActive: Boolean,
    expanded: Boolean,
    ui: TeamsUiState,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onNameDraft: (String) -> Unit,
    onPhoneDraft: (String) -> Unit,
    onPickContact: (com.example.billiardtracker.data.contacts.Contact) -> Unit,
    onRequestContacts: () -> Unit,
) {
    Card(
        onClick = { onExpand(); onSetActive() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        team.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${team.players.size} " + pluralPlayers(team.players.size) +
                            if (isActive) " · активная" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = "Переименовать")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (expanded) {
                team.players.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (p.phone != null) {
                                Text(
                                    formatPhone(p.phone.filter { it.isDigit() }),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        TextButton(onClick = { onRemovePlayer(i) }) { Text("Убрать") }
                    }
                }

                OutlinedTextField(
                    value = ui.nameDraft,
                    onValueChange = onNameDraft,
                    label = { Text("Имя игрока") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = ui.filteredContacts
                when {
                    suggestions.isNotEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggestions.forEach { c ->
                                TextButton(
                                    onClick = { onPickContact(c) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(c.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            formatPhone(c.phone.filter { it.isDigit() }),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    !ui.contactsGranted && ui.nameDraft.isNotBlank() -> {
                        OutlinedButton(
                            onClick = onRequestContacts,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Разрешить доступ к контактам") }
                    }
                }
                PhoneMaskInput(
                    value = ui.phoneDraft,
                    onChange = onPhoneDraft,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onAddPlayer, modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить игрока")
                }
                Button(
                    onClick = onCollapse,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить команду") }
            }
        }
    }
}

@Composable
private fun NameTeamDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        },
    )
}

private fun pluralPlayers(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "игроков"
        mod10 == 1 -> "игрок"
        mod10 in 2..4 -> "игрока"
        else -> "игроков"
    }
}
