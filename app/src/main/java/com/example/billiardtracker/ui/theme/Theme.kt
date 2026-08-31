package com.example.billiardtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v1.24.0: 5 палитр × dark/light. Юзер выбирает в Настройках.
 *
 * Стандартный маппинг для каждой палитры (кроме Original — там сохранил
 * привычную семантику «зелёное сукно + жёлтый акцент»):
 *  - `background`    — самый тёмный swatch (dark mode) / самый светлый (light)
 *  - `surface`       — соседний оттенок (Card, TextField, Dialog)
 *  - `surfaceContainer` — TopBar / NavBar
 *  - `primary`       — самый «яркий»/выделяющийся swatch → Button, Switch, active tab
 *  - `onPrimary`     — контраст к primary
 */
enum class AppColorScheme(val displayName: String) {
    ORIGINAL("Сукно (original)"),
    EARTH("Кофе"),
    PERIWINKLE("Лотус"),
    RETRO("Ярко №1"),
    VIVID("Ярко №2"),
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

private val OriginalLight = lightColorScheme(
    primary = AccentGoldDark,
    onPrimary = Color.White,
    primaryContainer = AccentGold,
    onPrimaryContainer = FeltDeep,
    secondary = FeltGreen,
    onSecondary = Color.White,
    tertiary = FeltGreen,
    background = BallCream,
    onBackground = FeltDeep,
    surface = Color.White,
    onSurface = FeltDeep,
    surfaceVariant = Color(0xFFECE7D0),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainer = Color(0xFFE0DAC0),
    surfaceContainerHigh = Color(0xFFD5CFB4),
    surfaceContainerHighest = Color(0xFFCAC4A7),
    error = ErrorRed,
    onError = Color.White,
    outline = AccentGoldDark,
    outlineVariant = Color(0xFFBFB99C),
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

private val EarthLight = lightColorScheme(
    primary = EarthPlum,
    onPrimary = EarthKhaki,
    primaryContainer = EarthAshBrown,
    onPrimaryContainer = EarthKhaki,
    secondary = EarthAshBrown,
    onSecondary = EarthKhaki,
    tertiary = EarthTaupe,
    background = Color(0xFFF5EBDC),
    onBackground = EarthPlum,
    surface = EarthKhaki,
    onSurface = EarthPlum,
    surfaceVariant = EarthTaupe,
    onSurfaceVariant = EarthPlum,
    surfaceContainer = EarthTaupe,
    surfaceContainerHigh = EarthDim,
    surfaceContainerHighest = EarthAshBrown,
    error = ErrorRed,
    onError = Color.White,
    outline = EarthPlum,
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

private val PeriwinkleLight = lightColorScheme(
    primary = PeriMain,
    onPrimary = Color.White,
    primaryContainer = PeriLight,
    onPrimaryContainer = PeriDark,
    secondary = PeriLavender,
    onSecondary = Color.White,
    tertiary = PeriMain,
    background = Color(0xFFF0F0FB),
    onBackground = PeriDark,
    surface = Color.White,
    onSurface = PeriDark,
    surfaceVariant = PeriLight,
    onSurfaceVariant = PeriDark,
    surfaceContainer = PeriLight,
    surfaceContainerHigh = Color(0xFFA6A2E5),
    surfaceContainerHighest = PeriMain,
    error = ErrorRed,
    onError = Color.White,
    outline = PeriMain,
    outlineVariant = PeriLavender,
)

// --- RETRO (crimson/indigo) -------------------------------------------

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

private val RetroLight = lightColorScheme(
    primary = RetroCrimson,
    onPrimary = Color.White,
    primaryContainer = RetroFlame,
    onPrimaryContainer = Color.White,
    secondary = RetroTeal,
    onSecondary = Color.White,
    tertiary = RetroIndigo,
    onTertiary = RetroLime,
    background = Color(0xFFFAF7EA),
    onBackground = RetroIndigo,
    surface = Color.White,
    onSurface = RetroIndigo,
    surfaceVariant = RetroLime,
    onSurfaceVariant = RetroIndigo,
    surfaceContainer = RetroLime,
    surfaceContainerHigh = Color(0xFFB4CA5C),
    surfaceContainerHighest = Color(0xFFA3BB4B),
    error = RetroCrimson,
    onError = Color.White,
    outline = RetroIndigo,
    outlineVariant = RetroTeal,
)

// --- VIVID / «Ярко №2» ------------------------------------------------
// Юзерская правка: жёлтый фон + красные плашки/кнопки + зелёный/синий
// акцентами. Раньше было всё зелёное (primary=VividGreen) — не «ярко».

private val VividDark = darkColorScheme(
    // Плашки (Card/Button) — красный, кнопки-акцент — тоже красный.
    primary = VividRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA02128),
    onPrimaryContainer = Color.White,
    // Секондари/третиари — синий + зелёный, чтобы палитра «играла».
    secondary = VividBlue,
    onSecondary = Color.White,
    tertiary = VividGreen,
    onTertiary = Color.White,
    // Фон — насыщенный жёлто-оранжевый (юзер: «фон надо жёлтый»); в dark-варианте
    // чуть глубже light'а, чтобы белые текстовые надписи не слепили.
    background = Color(0xFFE09A00),
    onBackground = Color(0xFF2A1A00),
    surface = VividRed,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF8B1A22),
    onSurfaceVariant = Color(0xFFFFDCD8),
    // TopBar/NavBar — темнее чем background чтобы визуально отделить header.
    surfaceContainer = Color(0xFFB37A08),
    surfaceContainerHigh = Color(0xFFC9860E),
    surfaceContainerHighest = Color(0xFFDE9200),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = VividGreen,
    outlineVariant = Color(0xFF8B1A22),
)

private val VividLight = lightColorScheme(
    primary = VividRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF6B72),
    onPrimaryContainer = Color(0xFF3A0004),
    secondary = VividBlue,
    onSecondary = Color.White,
    tertiary = VividGreen,
    onTertiary = Color.White,
    background = VividOrange,
    onBackground = Color(0xFF3A2000),
    surface = VividRed,
    onSurface = Color.White,
    surfaceVariant = Color(0xFFFFB74D),
    onSurfaceVariant = Color(0xFF3A2000),
    surfaceContainer = Color(0xFFFFC947),
    surfaceContainerHigh = Color(0xFFFFB60F),
    surfaceContainerHighest = Color(0xFFE99A00),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = VividGreen,
    outlineVariant = Color(0xFFFFCC80),
)

@Composable
fun BilliardTrackerTheme(
    scheme: AppColorScheme = AppColorScheme.ORIGINAL,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when (scheme) {
        AppColorScheme.ORIGINAL -> if (darkTheme) OriginalDark else OriginalLight
        AppColorScheme.EARTH -> if (darkTheme) EarthDark else EarthLight
        AppColorScheme.PERIWINKLE -> if (darkTheme) PeriwinkleDark else PeriwinkleLight
        AppColorScheme.RETRO -> if (darkTheme) RetroDark else RetroLight
        AppColorScheme.VIVID -> if (darkTheme) VividDark else VividLight
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
