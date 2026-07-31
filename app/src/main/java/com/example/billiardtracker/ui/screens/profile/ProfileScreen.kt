package com.example.billiardtracker.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Профиль") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Данные для локального использования — сохраняются на устройстве, без верификации.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = ui.phone,
                onValueChange = viewModel::setPhone,
                label = { Text("Телефон") },
                placeholder = { Text("+79001234567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.name,
                onValueChange = viewModel::setName,
                label = { Text("Имя") },
                placeholder = { Text("Как показывать в турнирах") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.saved) "Сохранено ✓" else "Сохранить")
            }
        }
    }
}
