package com.example.billiardtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.util.PhoneMaskInput
import com.example.billiardtracker.util.digitsToE164
import kotlinx.coroutines.launch

/**
 * Modal that requests + verifies an SMS code and stores the JWT.
 * Used from features that need cloud auth (currently: master-tokens list).
 *
 * The dialog owns its state — parent only cares about success (which fires
 * [onLoggedIn]) and dismiss.
 */
@Composable
fun CloudLoginDialog(
    authRepo: AuthRepository,
    onLoggedIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    var phoneDigits by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(false) }
    var debugCode by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loggedIn) { if (loggedIn) onLoggedIn() }

    val e164 = digitsToE164(phoneDigits)
    val phoneValid = e164 != null
    val codeValid = code.matches(Regex("^\\d{4}$"))

    fun requestCode() {
        val phone = e164 ?: run {
            error = "Введите номер полностью"
            return
        }
        loading = true
        error = null
        scope.launch {
            authRepo.requestCode(phone).fold(
                onSuccess = { dbg ->
                    loading = false
                    codeRequested = true
                    debugCode = dbg
                },
                onFailure = { e ->
                    loading = false
                    error = e.message ?: "Не удалось получить код"
                },
            )
        }
    }

    fun verify() {
        val phone = e164 ?: return
        if (!codeValid) {
            error = "Код — 4 цифры"
            return
        }
        loading = true
        error = null
        scope.launch {
            authRepo.verify(phone, code).fold(
                onSuccess = {
                    loading = false
                    loggedIn = true
                },
                onFailure = { e ->
                    loading = false
                    error = e.message ?: "Неверный код"
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Нужен облачный вход") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Пути мастера хранятся на сервере — войди по номеру, " +
                        "чтобы твои ссылки были привязаны к тебе.",
                    style = MaterialTheme.typography.bodySmall,
                )
                PhoneMaskInput(
                    value = phoneDigits,
                    onChange = { phoneDigits = it; error = null },
                    label = "Твой номер",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (codeRequested) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(4); error = null },
                        label = { Text("Код из SMS") },
                        placeholder = { Text("1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    debugCode?.let {
                        Text(
                            "dev код: $it",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (codeRequested) {
                TextButton(
                    onClick = { verify() },
                    enabled = !loading && codeValid,
                ) { Text(if (loading) "Входим…" else "Войти") }
            } else {
                TextButton(
                    onClick = { requestCode() },
                    enabled = !loading && phoneValid,
                ) { Text(if (loading) "Отправляем…" else "Получить SMS-код") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Отмена") }
        },
    )
}
