package com.lifetrace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class LifeTraceThemeMode(val storedValue: String, val label: String) {
    SYSTEM("system", "系统"),
    CLASSIC("classic", "经典白"),
    MINT("mint", "薄荷绿"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun fromStored(value: String?): LifeTraceThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: CLASSIC
    }
}

private val ClassicLightColors = lightColorScheme(
    primary = Color(0xFF2F63C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF102A56),
    secondary = Color(0xFF56627A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E9F3),
    onSecondaryContainer = Color(0xFF252D3B),
    tertiary = Color(0xFF506B5F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8EEE3),
    onTertiaryContainer = Color(0xFF18372B),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFF3F5F9),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFE5E8EE),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFDFEFF),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    surfaceContainerHighest = Color(0xFFDDE3EC),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFCBD1DC),
)

private val MintLightColors = lightColorScheme(
    primary = Color(0xFF166B54),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEBDD),
    onPrimaryContainer = Color(0xFF0A382B),
    secondary = Color(0xFF52655D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE9E3),
    onSecondaryContainer = Color(0xFF24332D),
    tertiary = Color(0xFF4B6470),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7EAF3),
    onTertiaryContainer = Color(0xFF18333D),
    background = Color(0xFFF0F6F2),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF0F6F2),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE2E9E5),
    onSurfaceVariant = Color(0xFF424944),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFEFC),
    surfaceContainer = Color(0xFFF8FCFA),
    surfaceContainerHigh = Color(0xFFE3EEE8),
    surfaceContainerHighest = Color(0xFFD7E5DD),
    outline = Color(0xFF727974),
    outlineVariant = Color(0xFFB7C6BE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF082F6D),
    primaryContainer = Color(0xFF234A94),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFBEC6DB),
    onSecondary = Color(0xFF293043),
    secondaryContainer = Color(0xFF3F4659),
    onSecondaryContainer = Color(0xFFDCE2F0),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC5C6CE),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF1A1E25),
    surfaceContainer = Color(0xFF20252D),
    surfaceContainerHigh = Color(0xFF2A3039),
    surfaceContainerHighest = Color(0xFF353C47),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF4C535F),
)

@Composable
fun LifeTraceTheme(
    mode: LifeTraceThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (mode) {
        LifeTraceThemeMode.SYSTEM -> if (systemDark) DarkColors else ClassicLightColors
        LifeTraceThemeMode.CLASSIC -> ClassicLightColors
        LifeTraceThemeMode.MINT -> MintLightColors
        LifeTraceThemeMode.DARK -> DarkColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
