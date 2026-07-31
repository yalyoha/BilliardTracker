package com.example.billiardtracker.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.data.remote.dto.TokenDto
import com.example.billiardtracker.data.repo.AuthRepository
import com.example.billiardtracker.ui.components.BilliardTopBar
import com.example.billiardtracker.ui.components.CloudLoginDialog
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.util.ApkInstaller

private const val SHARE_BASE = "https://billiardtracker.alekseylosev.ru/live"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authRepo: AuthRepository,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val needsLogin by viewModel.needsCloudLogin.collectAsStateWithLifecycle()
    val tokensState by viewModel.tokens.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var tokenToDelete by remember { mutableStateOf<TokenDto?>(null) }

    // Auto-load tokens once the user is logged in.
    LaunchedEffect(needsLogin) {
        if (!needsLogin) viewModel.refreshTokens()
    }

    Scaffold(
        topBar = {
            BilliardTopBar(
                title = { Text("Настройки") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Версия: v${ui.currentVersionName}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Обновлять автоматически", style = MaterialTheme.typography.titleSmall)
                            Text("Проверять новые версии при запуске", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoCheck, onCheckedChange = viewModel::setAutoCheck)
                    }
                    Button(
                        onClick = { viewModel.checkNow(); showUpdateDialog = true },
                        enabled = !ui.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (ui.checking) "Проверяем…" else "Проверить обновления") }
                    if (ui.message != null) Text(ui.message!!, style = MaterialTheme.typography.bodySmall)
                }
            }

            MasterTokensCard(
                needsLogin = needsLogin,
                loading = tokensState.loading,
                tokens = tokensState.tokens,
                error = tokensState.error,
                onStartLogin = { showLoginDialog = true },
                onStartCreate = { showCreateDialog = true },
                onCopy = { token ->
                    val url = "$SHARE_BASE/$token"
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BilliardTracker share", url))
                    Toast.makeText(ctx, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                },
                onShare = { token ->
                    val url = "$SHARE_BASE/$token"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Смотри мои бильярдные турниры: $url")
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Отправить"))
                },
                onRotate = { viewModel.rotateToken(it.id) },
                onDelete = { tokenToDelete = it },
                onDismissError = viewModel::clearTokensError,
            )
        }

        val latest = ui.latest
        if (showUpdateDialog && latest != null && latest.versionCode > ui.currentVersionCode) {
            UpdatePromptDialog(
                latest = latest,
                onUpdate = {
                    showUpdateDialog = false
                    ApkInstaller.downloadAndInstall(ctx, latest.apkUrl, latest.versionName)
                },
                onDismiss = { showUpdateDialog = false },
                onSkip = { viewModel.skip(latest.versionCode); showUpdateDialog = false },
            )
        }

        if (showLoginDialog) {
            CloudLoginDialog(
                authRepo = authRepo,
                onLoggedIn = {
                    showLoginDialog = false
                    viewModel.refreshTokens()
                },
                onDismiss = { showLoginDialog = false },
            )
        }

        if (showCreateDialog) {
            CreateTokenDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createToken(name)
                    showCreateDialog = false
                },
            )
        }

        tokenToDelete?.let { t ->
            DeleteTokenDialog(
                token = t,
                onCancel = { tokenToDelete = null },
                onConfirm = {
                    viewModel.deleteToken(t.id)
                    tokenToDelete = null
                },
            )
        }
    }
}

@Composable
private fun MasterTokensCard(
    needsLogin: Boolean,
    loading: Boolean,
    tokens: List<TokenDto>,
    error: String?,
    onStartLogin: () -> Unit,
    onStartCreate: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRotate: (TokenDto) -> Unit,
    onDelete: (TokenDto) -> Unit,
    onDismissError: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Пути мастера", style = MaterialTheme.typography.titleMedium)
            Text(
                "Один \"путь\" = токен-ссылка для группы друзей. Все, у кого " +
                    "есть ссылка, увидят твои турниры онлайн через браузер.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (needsLogin) {
                Button(
                    onClick = onStartLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Начать путь мастера") }
            } else {
                if (tokens.isEmpty() && !loading) {
                    Text(
                        "Пока ни одного пути нет.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = onStartCreate,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Начать путь мастера") }
                } else {
                    tokens.forEachIndexed { idx, t ->
                        if (idx > 0) HorizontalDivider()
                        TokenRow(
                            token = t,
                            onCopy = { onCopy(t.token) },
                            onShare = { onShare(t.token) },
                            onRotate = { onRotate(t) },
                            onDelete = { onDelete(t) },
                        )
                    }
                    if (tokens.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onStartCreate,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("+ Ещё путь") }
                    }
                }
                if (loading) {
                    Text("Загрузка…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissError) { Text("Ок") }
                }
            }
        }
    }
}

@Composable
private fun TokenRow(
    token: TokenDto,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val name = token.name?.takeIf { it.isNotBlank() } ?: "Без названия"
        val subtitle = "${token.tournamentCount} ${pluralTurniry(token.tournamentCount)}"
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text("Скопировать")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Text("Отправить")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) {
                Text("Новая ссылка")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Удалить") }
        }
    }
}

private fun pluralTurniry(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "турниров"
        mod10 == 1 -> "турнир"
        mod10 in 2..4 -> "турнира"
        else -> "турниров"
    }
}

@Composable
private fun CreateTokenDialog(
    onDismiss: () -> Unit,
    onCreate: (String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый путь мастера") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Дай пути название, чтобы отличать его от других " +
                        "(например \"Дача с Андреем\"). Не обязательно.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Название пути") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim().ifEmpty { null }) }) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun DeleteTokenDialog(
    token: TokenDto,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val label = token.name?.takeIf { it.isNotBlank() } ?: "Без названия"
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Удалить путь?") },
        text = {
            Text(
                "⚠️ Удалить путь \"$label\"? Все ${token.tournamentCount} " +
                    "${pluralTurniry(token.tournamentCount)} и статистика по ним будут стёрты навсегда.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Удалить всё") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        },
    )
}
