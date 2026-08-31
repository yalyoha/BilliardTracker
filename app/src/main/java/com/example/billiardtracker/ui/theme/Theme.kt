package com.example.billiardtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// lightColorScheme используется в PureLightScheme ниже.

/**
 * v1.24.0: 5 палитр × dark/light. v1.24.4: 6 тем всегда «одноуровневые» —
 * dark/light toggle убран, отдельные схемы DARK/LIGHT занимают его место.
 *
 * Правила:
 *  - Бильярд/Кофе/Лотус/Маракуйя всегда с тёмным фоном (dark-only варианты).
 *  - Тёмная / Светлая — служебные монохромные схемы для «pure» dark/light.
 */
enum class AppColorScheme(val displayName: String) {
    ORIGINAL("Бильярд"),
    EARTH("Кофе"),
    PERIWINKLE("Лотус"),
    RETRO("Маракуйя"),
    DARK("Тёмная"),
    LIGHT("Светлая"),
    ;
    companion object {
        fun fromKey(key: String?): AppColorScheme = entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}

// --- ORIGINAL (v1.23.0 сукно) -----------------------------------------

private val OriginalDark = darkColorScheme(
    primary = AccentGold,
    onPrimary = FeltDeep,
    primaryContainer = AccentGoldDark,
    onPrimaryContainer = FeltDeep,
    secondary = AccentGold,
    onSecondary = FeltDeep,
    tertiary = AccentGold,
    background = FeltDark,
    onBackground = TextOnDark,
    surface = GraphiteSurface,
    onSurface = TextOnDark,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = TextMutedDark,
    surfaceContainer = FeltHeader,
    surfaceContainerHigh = FeltHeader,
    surfaceContainerHighest = FeltHeader,
    error = ErrorRed,
    onError = FeltDeep,
    outline = AccentGold,
    outlineVariant = GraphiteBorder,
)

// --- EARTH (khaki/plum) -----------------------------------------------

private val EarthDark = darkColorScheme(
    primary = EarthKhaki,
    onPrimary = EarthPlum,
    primaryContainer = EarthTaupe,
    onPrimaryContainer = EarthPlum,
    secondary = EarthTaupe,
    onSecondary = EarthPlum,
    tertiary = EarthKhaki,
    background = EarthPlum,
    onBackground = EarthKhaki,
    surface = EarthAshBrown,
    onSurface = EarthKhaki,
    surfaceVariant = EarthDim,
    onSurfaceVariant = Color(0xFFE0D3C2),
    surfaceContainer = Color(0xFF3E2A28),
    surfaceContainerHigh = EarthAshBrown,
    surfaceContainerHighest = EarthDim,
    error = ErrorRed,
    onError = EarthPlum,
    outline = EarthKhaki,
    outlineVariant = EarthDim,
)

// --- PERIWINKLE (сирень) ----------------------------------------------

private val PeriwinkleDark = darkColorScheme(
    primary = PeriLight,
    onPrimary = PeriDark,
    primaryContainer = PeriMain,
    onPrimaryContainer = PeriDark,
    secondary = PeriMain,
    onSecondary = PeriDark,
    tertiary = PeriLight,
    background = PeriDark,
    onBackground = PeriLight,
    surface = PeriDim,
    onSurface = PeriLight,
    surfaceVariant = PeriLavender,
    onSurfaceVariant = Color(0xFFE0E0F0),
    surfaceContainer = Color(0xFF4A484D),
    surfaceContainerHigh = PeriDim,
    surfaceContainerHighest = PeriLavender,
    error = ErrorRed,
    onError = PeriDark,
    outline = PeriLight,
    outlineVariant = PeriLavender,
)

// --- RETRO (crimson/indigo) → «Маракуйя» ------------------------------

private val RetroDark = darkColorScheme(
    primary = RetroCrimson,
    onPrimary = Color.White,
    primaryContainer = RetroFlame,
    onPrimaryContainer = Color.White,
    secondary = RetroTeal,
    onSecondary = Color.White,
    tertiary = RetroLime,
    onTertiary = RetroIndigo,
    background = RetroIndigo,
    onBackground = RetroLime,
    surface = Color(0xFF3A3560),
    onSurface = RetroLime,
    surfaceVariant = Color(0xFF4A4472),
    onSurfaceVariant = Color(0xFFD8D8F0),
    surfaceContainer = Color(0xFF262042),
    surfaceContainerHigh = Color(0xFF3A3560),
    surfaceContainerHighest = Color(0xFF4A4472),
    error = RetroCrimson,
    onError = Color.White,
    outline = RetroLime,
    outlineVariant = Color(0xFF4A4472),
)

// --- DARK / «Тёмная» — 5 near-blacks + белый текст --------------------

private val PureDarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = PureBlack4,
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = PureBlack1,
    onBackground = Color.White,
    surface = PureBlack2,
    onSurface = Color.White,
    surfaceVariant = PureBlack4,
    onSurfaceVariant = Color(0xFFC0C0C0),
    surfaceContainer = PureBlack3,
    surfaceContainerHigh = PureBlack3,
    surfaceContainerHighest = PureBlack4,
    error = ErrorRed,
    onError = Color.Black,
    outline = PureBlack5,
    outlineVariant = PureBlack4,
)

// --- LIGHT / «Светлая» — 5 near-whites + чёрный текст -----------------

private val PureLightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = PureWhite4,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    background = PureWhite1,
    onBackground = Color.Black,
    surface = PureWhite2,
    onSurface = Color.Black,
    surfaceVariant = PureWhite4,
    onSurfaceVariant = Color(0xFF3A3A3A),
    surfaceContainer = PureWhite3,
    surfaceContainerHigh = PureWhite3,
    surfaceContainerHighest = PureWhite4,
    error = ErrorRed,
    onError = Color.White,
    outline = PureWhite5,
    outlineVariant = PureWhite4,
)

// VIVID палитра удалена в v1.24.3. Original*Light/Earth*Light/Peri*Light/
// Retro*Light удалены в v1.24.4 — палитры теперь всегда dark-only, а роль
// светлого/тёмного играют отдельные схемы DARK/LIGHT.

/**
 * v1.24.4: `darkTheme` больше не используется — DARK/LIGHT схемы сами
 * заменяют этот тумблер. Оставил в сигнатуре для обратной совместимости
 * с существующими вызовами; можно удалить в след. релизе.
 */
@Composable
fun BilliardTrackerTheme(
    scheme: AppColorScheme = AppColorScheme.ORIGINAL,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when (scheme) {
        AppColorScheme.ORIGINAL -> OriginalDark
        AppColorScheme.EARTH -> EarthDark
        AppColorScheme.PERIWINKLE -> PeriwinkleDark
        AppColorScheme.RETRO -> RetroDark
        AppColorScheme.DARK -> PureDarkScheme
        AppColorScheme.LIGHT -> PureLightScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
