package com.lifetrace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class LifeTraceThemeMode(val storedValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
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
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFE5E8EE),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7FA),
    surfaceContainer = Color(0xFFF0F2F6),
    surfaceContainerHigh = Color(0xFFEAECF1),
    surfaceContainerHighest = Color(0xFFE4E7EC),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C7CF),
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
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF8FAF9),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE2E9E5),
    onSurfaceVariant = Color(0xFF424944),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF2F0),
    surfaceContainerHigh = Color(0xFFE8ECEA),
    surfaceContainerHighest = Color(0xFFE1E6E3),
    outline = Color(0xFF727974),
    outlineVariant = Color(0xFFC1C8C3),
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
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC5C6CE),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191B20),
    surfaceContainer = Color(0xFF1D2025),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
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
