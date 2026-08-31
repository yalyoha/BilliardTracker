package com.example.billiardtracker.ui.screens.gametype

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.contacts.ContactsReader
import com.example.billiardtracker.ui.components.BilliardTopBar
import com.example.billiardtracker.ui.nav.Team
import com.example.billiardtracker.ui.screens.team.TeamViewModel
import com.example.billiardtracker.ui.screens.team.TeamsUiState
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeSetupScreen(
    viewModel: StakeSetupViewModel,
    teamViewModel: TeamViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val teamUi by teamViewModel.ui.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()
    val activeTeamId by viewModel.activeTeamId.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadFromState() }
    LaunchedEffect(ui.createdTournamentId) { ui.createdTournamentId?.let(onCreated) }

    // GPS-permission — как раньше, чтобы автозаполнить название по ближайшему клубу.
    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.retryAutoTitle() }
    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!already) locPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // Contacts-permission — нужен для inline team-editor (см. TeamScreen).
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) teamViewModel.onContactsPermissionGranted(ContactsReader(ctx.contentResolver))
        else teamViewModel.onContactsPermissionDenied()
    }
    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        when {
            already -> teamViewModel.onContactsPermissionGranted(ContactsReader(ctx.contentResolver))
            !teamUi.contactsRequested -> contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var teamToRename by remember { mutableStateOf<Team?>(null) }
    var teamToDelete by remember { mutableStateOf<Team?>(null) }

    Scaffold(
        topBar = { BilliardTopBar(
            title = { Text("Встреча") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            actions = {
                Button(onClick = viewModel::submit, enabled = !ui.loading) {
                    Text(if (ui.loading) "…" else "Создать")
                }
            },
        ) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ui.title, onValueChange = viewModel::setTitle,
                label = { Text("Название") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (ui.nearbyClubs.isNotEmpty()) {
                ClubPickerDropdown(
                    clubs = ui.nearbyClubs,
                    onPick = viewModel::pickClub,
                )
            }

            // === Ставка + режим "за шар/за встречу" ===
            if (ui.moneyPlayable) {
                StakeModeToggle(
                    mode = ui.stakeMode,
                    onModeChange = viewModel::setStakeMode,
                )
                val stakeLabel = if (ui.stakeMode == "per_match") "Ставка ₽ за встречу" else "Ставка ₽ за шар"
                OutlinedTextField(
                    value = ui.stakeRub, onValueChange = viewModel::setStake,
                    label = { Text(stakeLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Эта дисциплина не подразумевает игру на деньги", style = MaterialTheme.typography.bodySmall)
            }

            // === Размер партии (0..1000, ±, default 8) ===
            GameSizeInput(
                size = ui.gameSize,
                onDecrement = viewModel::decGameSize,
                onIncrement = viewModel::incGameSize,
                onTextChange = viewModel::setGameSizeText,
            )

            WinsRequiredDropdown(
                value = ui.winsRequired,
                onChange = viewModel::setWinsRequired,
            )

            HorizontalDivider()

            // === Составы — inline picker + editor ===
            Text("Составы", style = MaterialTheme.typography.titleSmall)
            Text(
                "Тап по составу — выбрать активный. Иконка «карандаш» — редактировать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (teams.isEmpty()) {
                Text(
                    "Ещё нет составов. Создай первый — с ним стартует встреча.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                teams.forEach { team ->
                    InlineTeamCard(
                        team = team,
                        isActive = team.id == activeTeamId,
                        expanded = team.id == teamUi.expandedTeamId,
                        ui = teamUi,
                        onSetActive = { viewModel.selectTeam(team.id) },
                        onExpand = { teamViewModel.expandTeam(team.id) },
                        onCollapse = teamViewModel::collapseTeam,
                        onRename = { teamToRename = team },
                        onDelete = { teamToDelete = team },
                        onAddPlayer = { teamViewModel.addPlayer(team.id) },
                        onRemovePlayer = { idx -> teamViewModel.removePlayer(team.id, idx) },
                        onNameDraft = teamViewModel::setNameDraft,
                        onPhoneDraft = teamViewModel::setPhoneDraft,
                        onPickContact = teamViewModel::pickContact,
                        onRequestContacts = {
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                    )
                }
            }
            Button(
                onClick = { showCreateTeamDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать состав") }

            HorizontalDivider()
            Text("Участники (фора и индивидуальная ставка)", style = MaterialTheme.typography.titleSmall)
            if (ui.perParticipant.isEmpty()) {
                Text(
                    "Выбери состав выше — участники появятся здесь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.perParticipant.forEachIndexed { i, p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                                    if (p.phone != null) Text(p.phone, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { viewModel.removeParticipant(i) }) { Text("×") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Фора +", modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = p.handicapPoints.toString(),
                                    onValueChange = { s -> viewModel.setHandicap(i, s.toIntOrNull() ?: 0) },
                                    singleLine = true, modifier = Modifier.width(80.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Text(" очков", modifier = Modifier.weight(1f))
                            }
                            if (ui.moneyPlayable) {
                                val overrideLabel = if (ui.stakeMode == "per_match") "Ставка ₽/встреча" else "Ставка ₽/шар"
                                OutlinedTextField(
                                    value = p.overrideRub,
                                    onValueChange = { s -> viewModel.setOverride(i, s) },
                                    label = { Text(overrideLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            if (!ui.ownerAlreadyIn) {
                OutlinedButton(
                    onClick = viewModel::addOwnerAsParticipant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val label = if (ui.ownerName.isBlank()) "Добавить владельца телефона"
                                else "Добавить себя — ${ui.ownerName}"
                    Text(label)
                }
            }

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showCreateTeamDialog) {
        NameTeamDialog(
            title = "Новый состав",
            initial = "",
            onDismiss = { showCreateTeamDialog = false },
            onSave = { name ->
                teamViewModel.createTeam(name)
                showCreateTeamDialog = false
            },
        )
    }
    teamToRename?.let { t ->
        NameTeamDialog(
            title = "Переименовать состав",
            initial = t.name,
            onDismiss = { teamToRename = null },
            onSave = { name ->
                teamViewModel.renameTeam(t.id, name)
                teamToRename = null
            },
        )
    }
    teamToDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            title = { Text("Удалить состав?") },
            text = { Text("Удалить \"${t.name}\" и всех его игроков?") },
            confirmButton = {
                Button(
                    onClick = { teamViewModel.deleteTeam(t.id); teamToDelete = null },
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
private fun StakeModeToggle(mode: String, onModeChange: (String) -> Unit) {
    val isPerBall = mode != "per_match"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val perBallColors = if (isPerBall)
            ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
        val perMatchColors = if (!isPerBall)
            ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
        if (isPerBall) {
            Button(
                onClick = { onModeChange("per_ball") },
                modifier = Modifier.weight(1f),
                colors = perBallColors,
            ) { Text("За шар") }
            OutlinedButton(
                onClick = { onModeChange("per_match") },
                modifier = Modifier.weight(1f),
            ) { Text("За встречу") }
        } else {
            OutlinedButton(
                onClick = { onModeChange("per_ball") },
                modifier = Modifier.weight(1f),
            ) { Text("За шар") }
            Button(
                onClick = { onModeChange("per_match") },
                modifier = Modifier.weight(1f),
                colors = perMatchColors,
            ) { Text("За встречу") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameSizeInput(
    size: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Размер партии (шаров)", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = size > 0,
                modifier = Modifier.width(56.dp),
            ) { Text("−") }
            OutlinedTextField(
                value = size.toString(),
                onValueChange = onTextChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(
                onClick = onIncrement,
                enabled = size < 1000,
                modifier = Modifier.width(56.dp),
            ) { Text("+") }
        }
        Text(
            "От 0 до 1000. По умолчанию — 8.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineTeamCard(
    team: Team,
    isActive: Boolean,
    expanded: Boolean,
    ui: TeamsUiState,
    onSetActive: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
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
        onClick = onSetActive,
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
                            if (isActive) " · активный" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { if (expanded) onCollapse() else onExpand() }) {
                    Icon(Icons.Filled.Edit, contentDescription = if (expanded) "Свернуть" else "Редактировать")
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
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Переименовать состав") }

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
                ) { Text("Готово") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubPickerDropdown(
    clubs: List<com.example.billiardtracker.data.remote.dto.ClubDto>,
    onPick: (com.example.billiardtracker.data.remote.dto.ClubDto) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "Выбрать другую бильярдную",
            onValueChange = {},
            readOnly = true,
            label = { Text("Ближайшие бильярдные (${clubs.size})") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            clubs.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(c.name)
                            val dist = c.distanceM
                            val sub = if (dist != null) {
                                if (dist < 1000) "$dist м" else "%.1f км".format(dist / 1000.0)
                            } else c.address ?: ""
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    onClick = { onPick(c); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WinsRequiredDropdown(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "$value",
            onValueChange = {},
            readOnly = true,
            label = { Text("До скольких побед") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (1..10).forEach { n ->
                DropdownMenuItem(
                    text = { Text("$n") },
                    onClick = {
                        onChange(n)
                        expanded = false
                    },
                )
            }
        }
    }
}
