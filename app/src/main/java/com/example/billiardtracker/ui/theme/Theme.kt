package com.example.billiardtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Фон — зелёное сукно. TopBar/NavBar — темнее зелёный (через surfaceContainer).
// Card/Dialog — нейтральные серые (через surface). Жёлтый — единственный акцент.
private val BilliardColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = FeltDeep,
    primaryContainer = AccentGoldDark,
    onPrimaryContainer = FeltDeep,
    secondary = AccentGold,
    onSecondary = FeltDeep,
    tertiary = AccentGold,
    background = FeltDark,
    onBackground = TextOnDark,
    // surface — для Card, Dialog, TextField-fill (серый).
    surface = GraphiteSurface,
    onSurface = TextOnDark,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = TextMutedDark,
    // surfaceContainer — по умолчанию NavigationBar его использует, зелёный.
    surfaceContainer = FeltHeader,
    surfaceContainerHigh = FeltHeader,
    surfaceContainerHighest = FeltHeader,
    error = ErrorRed,
    onError = FeltDeep,
    outline = AccentGold,
    outlineVariant = GraphiteBorder,
)

@Composable
fun BilliardTrackerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BilliardColorScheme,
        typography = Typography,
        content = content,
    )
}
