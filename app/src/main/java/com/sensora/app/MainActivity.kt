package com.sensora.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.ui.nav.SensoraNavHost
import com.sensora.app.ui.theme.SensoraTheme

/** AppCompatActivity is required for in-app language switching on Android 12 and lower. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as SensoraApp
        setContent {
            val themeMode by app.prefs.themeMode.collectAsStateWithLifecycle()
            SensoraTheme(themeMode) { SensoraNavHost() }
        }
    }
}
