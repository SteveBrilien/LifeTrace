package com.lifetrace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifetrace.app.ui.LifeTraceApp
import com.lifetrace.app.ui.theme.LifeTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeTraceTheme {
                LifeTraceApp()
            }
        }
    }
}
