package com.example.billiardtracker.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.digitsToE164
import com.example.billiardtracker.util.formatPhone
import kotlinx.coroutines.launch

/**
 * First-run screen shown when the user has no JWT locally. Blocks the app until
 * they register (no-SMS) and create their first master-token. Two stages:
 *   1. Form: Имя + Телефон.
 *   2. Confirm dialog "Всё верно?" — protects against typos in a critical field.
 *
 * On success `onDone` is called; caller swaps in the normal navigation host.
 */
@Composable
fun OnboardingScreen(
    authRepo: AuthRepository,
    onDone: () -> Unit,
) {
    var phoneDigits by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val e164 = digitsToE164(phoneDigits)
    val phoneValid = e164 != null
    val nameValid = name.trim().isNotEmpty()
    val formValid = phoneValid && nameValid

    fun submit() {
        val phone = e164 ?: return
        val trimmedName = name.trim()
        loading = true
        error = null
        // rememberCoroutineScope is safe here because JWT/activeTokenId are
        // persisted atomically *inside* the repo call — if the composition
        // dies mid-call we haven't half-onboarded the user; retry is safe.
        scope.launch {
            authRepo.registerAndCreateFirstToken(phone, trimmedName, "Путь мастера №1").fold(
                onSuccess = {
                    loading = false
                    onDone()
                },
                onFailure = { e ->
                    loading = false
                    showConfirm = false
                    error = e.message ?: "Не удалось сохранить"
                },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BilliardTracker", style = MaterialTheme.typography.headlineSmall)
        Column(
            Modifier.padding(top = 24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "* регистрация без смс подтверждения",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PhoneMaskInput(
                value = phoneDigits,
                onChange = { phoneDigits = it; error = null },
                label = "Телефон",
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { showConfirm = true },
                enabled = !loading && formValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (loading) "Сохраняем…" else "Начать путь мастера") }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!loading) showConfirm = false },
            title = { Text("Всё верно?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Проверь данные — они запишутся в облако:", style = MaterialTheme.typography.bodySmall)
                    Text("Имя: ${name.trim()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Телефон: ${formatPhone(phoneDigits)}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showConfirm = false },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Изменить") }
                    Button(
                        onClick = { submit() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (loading) "Сохраняем…" else "Сохранить") }
                }
            },
            dismissButton = null,
        )
    }
}
