package com.example.billiardtracker.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.components.BilliardTopBar
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { BilliardTopBar(title = { Text("Профиль") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Данные для локального использования — сохраняются на устройстве, без верификации.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (ui.editing) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = ui.name,
                            onValueChange = viewModel::setName,
                            label = { Text("Имя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PhoneMaskInput(
                            value = ui.phoneDigits,
                            onChange = viewModel::setPhoneDigits,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                            Text(if (ui.saved) "Сохранено ✓" else "Сохранить")
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Имя", style = MaterialTheme.typography.labelSmall)
                        Text(
                            ui.name.ifBlank { "—" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Телефон", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (ui.phoneDigits.isNotBlank()) formatPhone(ui.phoneDigits) else "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                OutlinedButton(
                    onClick = viewModel::startEdit,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Изменить")
                }
            }
        }
    }
}
