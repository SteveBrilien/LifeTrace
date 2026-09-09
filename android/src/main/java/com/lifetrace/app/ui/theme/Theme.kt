package com.lifetrace.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6D57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFE4),
    onPrimaryContainer = Color(0xFF07271D),
    background = Color(0xFFF7F9F7),
    surface = Color(0xFFF7F9F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD4BC),
    onPrimary = Color(0xFF063827),
    primaryContainer = Color(0xFF17513F),
    onPrimaryContainer = Color(0xFFD6EFE4),
)

@Composable
fun LifeTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
