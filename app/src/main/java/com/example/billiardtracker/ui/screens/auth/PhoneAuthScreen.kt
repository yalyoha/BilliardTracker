package com.example.billiardtracker.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PhoneAuthScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.loggedIn) { if (ui.loggedIn) onLoggedIn() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BilliardTracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = ui.phone,
            onValueChange = viewModel::setPhone,
            label = { Text("Телефон") },
            placeholder = { Text("+79001234567") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::requestCode,
            enabled = !ui.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ui.codeRequested) "Получить код повторно" else "Получить код")
        }

        if (ui.codeRequested) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = ui.code,
                onValueChange = viewModel::setCode,
                label = { Text("Код из SMS") },
                placeholder = { Text("1234") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (ui.debugCode != null) {
                Text(
                    "dev код: ${ui.debugCode}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::verify,
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Войти")
            }
        }

        if (ui.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(ui.error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
