package com.example.billiardtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Единая палитра «сукно» — та же в light/dark, потому что бренд.
private val BilliardColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = FeltDeep,
    primaryContainer = AccentGoldDark,
    onPrimaryContainer = FeltDeep,
    secondary = BallCream,
    onSecondary = FeltDeep,
    tertiary = AccentGold,
    background = FeltDark,
    onBackground = TextOnDark,
    surface = SurfaceElevated,
    onSurface = TextOnDark,
    surfaceVariant = FeltGreen,
    onSurfaceVariant = TextMutedDark,
    error = ErrorRed,
    onError = FeltDeep,
    outline = TextMutedDark,
    outlineVariant = FeltGreen,
)

@Composable
fun BilliardTrackerTheme(
    darkTheme: Boolean = true, // всегда «сукно»-палитра
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BilliardColorScheme,
        typography = Typography,
        content = content,
    )
}
