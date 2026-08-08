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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import com.example.billiardtracker.data.remote.dto.TokenDto
import com.example.billiardtracker.ui.components.BilliardTopBar
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.ui.components.UpdateStage
import com.example.billiardtracker.util.ApkInstaller
import com.example.billiardtracker.util.InstallResult
import kotlinx.coroutines.launch

private const val SHARE_BASE = "https://billiardtracker.alekseylosev.ru/live"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenClubs: () -> Unit,
    onOpenRules: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val tokensState by viewModel.tokens.collectAsStateWithLifecycle()
    val activeTokenId by viewModel.activeTokenId.collectAsStateWithLifecycle()
    val enabledSlugs by viewModel.enabledGameSlugs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateStage by remember { mutableStateOf<UpdateStage>(UpdateStage.Idle) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var tokenToRename by remember { mutableStateOf<TokenDto?>(null) }
    var tokenToDelete by remember { mutableStateOf<TokenDto?>(null) }

    // Refresh happens in VM init — reliable across recompositions and tab
    // switches. No LaunchedEffect needed here.

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

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Правила дисциплин", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Справочник правил 14 дисциплин русского бильярда.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = onOpenRules,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Правила игр") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Дисциплины на главном", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Отметь только те игры, которые показывать на главном экране при выборе встречи. Полный справочник всегда доступен в «Правилах игр».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    com.example.billiardtracker.domain.rules.GameType.entries.forEach { gt ->
                        val checked = gt.ruleFileSlug in enabledSlugs
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(gt.displayName, modifier = Modifier.weight(1f))
                            Switch(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleGameSlug(gt.ruleFileSlug, it) },
                                enabled = !(checked && enabledSlugs.size == 1),
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Бильярдные", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Общий список заведений. Название бильярдной используется в автоматическом заголовке новой встречи.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = onOpenClubs,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Управлять бильярдными") }
                }
            }

            MasterTokensCard(
                loading = tokensState.loading,
                tokens = tokensState.tokens,
                activeTokenId = activeTokenId,
                error = tokensState.error,
                onActivate = { viewModel.setActiveToken(it.id) },
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
                        putExtra(Intent.EXTRA_TEXT, "Смотри мои встречи в бильярдной: $url")
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Отправить"))
                },
                onRename = { tokenToRename = it },
                onRotate = { viewModel.rotateToken(it.id) },
                onDelete = { tokenToDelete = it },
                onDismissError = viewModel::clearTokensError,
            )
        }

        val latest = ui.latest
        if (showUpdateDialog && latest != null && latest.versionCode > ui.currentVersionCode) {
            UpdatePromptDialog(
                latest = latest,
                stage = updateStage,
                onUpdate = {
                    scope.launch {
                        updateStage = UpdateStage.Downloading(0f)
                        val result = ApkInstaller.downloadAndInstall(
                            context = ctx,
                            apkUrl = latest.apkUrl,
                            versionName = latest.versionName,
                            onProgress = { updateStage = UpdateStage.Downloading(it) },
                        )
                        when (result) {
                            is InstallResult.Success -> {
                                updateStage = UpdateStage.Idle
                                showUpdateDialog = false
                            }
                            is InstallResult.NeedInstallPermission -> {
                                Toast.makeText(
                                    ctx,
                                    "Разреши установку из этого приложения и повтори",
                                    Toast.LENGTH_LONG,
                                ).show()
                                ApkInstaller.openInstallPermissionSettings(ctx)
                                updateStage = UpdateStage.Idle
                            }
                            is InstallResult.Error -> {
                                updateStage = UpdateStage.Error(result.message)
                            }
                        }
                    }
                },
                onDismiss = { showUpdateDialog = false; updateStage = UpdateStage.Idle },
                onSkip = {
                    viewModel.skip(latest.versionCode)
                    showUpdateDialog = false
                    updateStage = UpdateStage.Idle
                },
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

        tokenToRename?.let { t ->
            RenameTokenDialog(
                token = t,
                onCancel = { tokenToRename = null },
                onSave = { newName ->
                    viewModel.renameToken(t.id, newName)
                    tokenToRename = null
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
    loading: Boolean,
    tokens: List<TokenDto>,
    activeTokenId: Long?,
    error: String?,
    onActivate: (TokenDto) -> Unit,
    onStartCreate: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRename: (TokenDto) -> Unit,
    onRotate: (TokenDto) -> Unit,
    onDelete: (TokenDto) -> Unit,
    onDismissError: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Пути мастера", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ссылка с токеном для тебя и друзей, для сохранения и отслеживания встреч.",
                style = MaterialTheme.typography.bodySmall,
            )

            tokens.forEachIndexed { idx, t ->
                if (idx > 0) HorizontalDivider()
                TokenRow(
                    token = t,
                    isActive = t.id == activeTokenId,
                    onActivate = { onActivate(t) },
                    onCopy = { onCopy(t.token) },
                    onShare = { onShare(t.token) },
                    onRename = { onRename(t) },
                    onRotate = { onRotate(t) },
                    onDelete = { onDelete(t) },
                )
            }
            if (loading && tokens.isEmpty()) {
                Text("Загрузка…", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onStartCreate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Новый путь мастера") }

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
    isActive: Boolean,
    onActivate: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val name = token.name?.takeIf { it.isNotBlank() } ?: "Без названия"
        val subtitle = "${token.tournamentCount} ${pluralVstrech(token.tournamentCount)}"
        val isOwner = token.role != "subscriber"
        if (isActive) {
            Button(
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TokenRowContent(
                    name = name,
                    subtitle = subtitle,
                    isOwner = isOwner,
                    iconTint = MaterialTheme.colorScheme.onPrimary,
                    onRename = onRename,
                    onShare = onShare,
                )
            }
        } else {
            OutlinedButton(
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TokenRowContent(
                    name = name,
                    subtitle = subtitle,
                    isOwner = isOwner,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onRename = onRename,
                    onShare = onShare,
                )
            }
        }
        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Text("Скопировать ссылку")
        }
        if (isOwner) {
            OutlinedButton(onClick = onRotate, modifier = Modifier.fillMaxWidth()) {
                Text("Новая ссылка")
            }
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) { Text(if (isOwner) "Удалить" else "Отписаться") }
    }
}

@Composable
private fun TokenRowContent(
    name: String,
    subtitle: String,
    isOwner: Boolean,
    iconTint: androidx.compose.ui.graphics.Color,
    onRename: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                if (isOwner) subtitle else "$subtitle · подписан",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isOwner) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = iconTint)
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = iconTint)
        }
    }
}

@Composable
private fun RenameTokenDialog(
    token: TokenDto,
    onCancel: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var name by remember { mutableStateOf(token.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Переименовать путь") },
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
                onClick = { onSave(name.trim().ifEmpty { null }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        },
    )
}

private fun pluralVstrech(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "встреч"
        mod10 == 1 -> "встреча"
        mod10 in 2..4 -> "встречи"
        else -> "встреч"
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
                    "${pluralVstrech(token.tournamentCount)} и статистика по ним будут стёрты навсегда.",
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
