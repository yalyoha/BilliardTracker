package com.example.billiardtracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

val LocalPendingSync = compositionLocalOf { 0 }
val LocalOnPendingSync = compositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilliardTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val pendingSync = LocalPendingSync.current
    val onPendingSync = LocalOnPendingSync.current
    val isLandscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    if (isLandscape) {
        // В горизонтальном режиме: кастомный Row без лишнего 16dp-отступа у заголовка,
        // который Material3 TopAppBar добавляет принудительно.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationIcon()
                    Box(Modifier.weight(1f).padding(start = 4.dp)) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                            LocalTextStyle provides MaterialTheme.typography.titleMedium,
                        ) {
                            title()
                        }
                    }
                    if (pendingSync > 0) {
                        TextButton(onClick = onPendingSync) {
                            Text(
                                "⏳ $pendingSync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    actions()
                }
            }
        }
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = {
                if (pendingSync > 0) {
                    TextButton(onClick = onPendingSync) {
                        Text(
                            "⏳ $pendingSync",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.primary,
                actionIconContentColor = MaterialTheme.colorScheme.primary,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
    }
}
