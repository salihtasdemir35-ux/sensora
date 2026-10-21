package com.sensora.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sensora.app.data.ThemeMode

val GoodColor = Color(0xFF2E7D32)
val LimitedColor = Color(0xFFE09A2B)
val BadColor = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E79), onPrimary = Color.White,
    primaryContainer = Color(0xFFB6ECF2), onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF4A6267), secondaryContainer = Color(0xFFCDE7EC),
    tertiary = Color(0xFFB8741A), onTertiary = Color.White,
    background = Color(0xFFF6FAFB), surface = Color(0xFFF6FAFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3E0), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58), onPrimaryContainer = Color(0xFFB6ECF2),
    secondary = Color(0xFFB1CBD0), secondaryContainer = Color(0xFF334B4F),
    tertiary = Color(0xFFF4B860), onTertiary = Color(0xFF452B00),
    background = Color(0xFF0E1416), surface = Color(0xFF0E1416),
)

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun SensoraTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
