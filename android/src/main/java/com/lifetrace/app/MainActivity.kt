package com.lifetrace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifetrace.app.ui.LifeTraceApp
import com.lifetrace.app.ui.theme.LifeTraceTheme
import com.lifetrace.app.ui.theme.LifeTraceThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember {
                getSharedPreferences("appearance", MODE_PRIVATE)
            }
            var themeMode by remember {
                mutableStateOf(
                    LifeTraceThemeMode.fromStored(preferences.getString("theme_mode", null)),
                )
            }
            LifeTraceTheme(mode = themeMode) {
                LifeTraceApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        preferences.edit().putString("theme_mode", mode.storedValue).apply()
                    },
                )
            }
        }
    }
}
