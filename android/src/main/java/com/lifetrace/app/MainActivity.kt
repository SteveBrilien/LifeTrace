package com.lifetrace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifetrace.app.ui.LifeTraceApp
import com.lifetrace.app.ui.MapLabelLanguage
import com.lifetrace.app.ui.theme.LifeTraceTheme
import com.lifetrace.app.ui.theme.LifeTraceThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemNavigationBar()
        setContent {
            val preferences = remember {
                getSharedPreferences("appearance", MODE_PRIVATE)
            }
            var themeMode by remember {
                mutableStateOf(
                    LifeTraceThemeMode.fromStored(preferences.getString("theme_mode", null)),
                )
            }
            var mapLabelLanguage by remember {
                mutableStateOf(
                    MapLabelLanguage.fromStored(preferences.getString("map_label_language", null)),
                )
            }
            LifeTraceTheme(mode = themeMode) {
                LifeTraceApp(
                    themeMode = themeMode,
                    mapLabelLanguage = mapLabelLanguage,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        preferences.edit().putString("theme_mode", mode.storedValue).apply()
                    },
                    onMapLabelLanguageChange = { language ->
                        mapLabelLanguage = language
                        preferences.edit()
                            .putString("map_label_language", language.storedValue)
                            .apply()
                    },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }

    private fun hideSystemNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
