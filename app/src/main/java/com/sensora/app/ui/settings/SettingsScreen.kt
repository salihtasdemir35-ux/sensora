package com.sensora.app.ui.settings

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.BuildConfig
import com.sensora.app.R
import com.sensora.app.data.ThemeMode
import com.sensora.app.ui.common.*

/** Language names are written in their own script so anyone can find their language. */
private val LANGUAGES = listOf("en" to "English", "tr" to "Türkçe", "de" to "Deutsch", "es" to "Español", "ar" to "العربية")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    val theme by prefs.themeMode.collectAsStateWithLifecycle()
    val currentTag = remember { AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-') }
    val resetMsg = stringResource(R.string.calibration_reset_done)
    val clearedMsg = stringResource(R.string.location_cleared)
    val observer by prefs.observer.collectAsStateWithLifecycle()

    SimpleScaffold(stringResource(R.string.settings), onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            InfoCard {
                RadioLine(stringResource(R.string.lang_system), currentTag.isEmpty()) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                LANGUAGES.forEach { (tag, name) ->
                    RadioLine(name, currentTag == tag) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                }
            }
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            InfoCard {
                RadioLine(stringResource(R.string.theme_system), theme == ThemeMode.SYSTEM) { prefs.setThemeMode(ThemeMode.SYSTEM) }
                RadioLine(stringResource(R.string.theme_light), theme == ThemeMode.LIGHT) { prefs.setThemeMode(ThemeMode.LIGHT) }
                RadioLine(stringResource(R.string.theme_dark), theme == ThemeMode.DARK) { prefs.setThemeMode(ThemeMode.DARK) }
            }
            Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
            InfoCard {
                OutlinedButton(onClick = { prefs.resetCalibrations(); Toast.makeText(ctx, resetMsg, Toast.LENGTH_SHORT).show() }) {
                    Text(stringResource(R.string.settings_reset_calibration))
                }
                OutlinedButton(enabled = observer != null, onClick = { prefs.clearObserver(); Toast.makeText(ctx, clearedMsg, Toast.LENGTH_SHORT).show() }) {
                    Text(stringResource(R.string.settings_clear_location))
                }
            }
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
            InfoCard { Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
