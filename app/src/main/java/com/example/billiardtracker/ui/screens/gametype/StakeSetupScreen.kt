package com.example.billiardtracker.ui.screens.gametype

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeSetupScreen(
    viewModel: StakeSetupViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadFromState() }
    LaunchedEffect(ui.createdTournamentId) { ui.createdTournamentId?.let(onCreated) }

    // Запрашиваем GPS-permission один раз при входе — нужно чтобы автозаполнить
    // "[Клуб] № N" по ближайшему бару. Если пользователь отклонит — форма
    // просто останется с пустым названием (юзер введёт вручную).
    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.retryAutoTitle() }
    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!already) locPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Scaffold(
        topBar = { BilliardTopBar(
            title = { Text("Настройка турнира") },
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
            Text(ui.gameTypeName, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = ui.title, onValueChange = viewModel::setTitle,
                label = { Text("Название") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            if (ui.moneyPlayable) {
                OutlinedTextField(
                    value = ui.stakeRub, onValueChange = viewModel::setStake,
                    label = { Text("Ставка ₽ за шар") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Эта дисциплина не подразумевает игру на деньги", style = MaterialTheme.typography.bodySmall)
            }

            WinsRequiredDropdown(
                value = ui.winsRequired,
                onChange = viewModel::setWinsRequired,
            )

            Divider()
            Text("Участники (гандикап и индивидуальная ставка)", style = MaterialTheme.typography.titleSmall)
            Text("Ты автоматически добавляешься как маркёр и первый участник — здесь настраиваются только приглашённые.",
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ui.perParticipant.forEachIndexed { i, p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                        if (p.phone != null) Text(p.phone, style = MaterialTheme.typography.bodySmall)
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
                            OutlinedTextField(
                                value = p.overrideRub,
                                onValueChange = { s -> viewModel.setOverride(i, s) },
                                label = { Text("Ставка ₽/шар") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
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
