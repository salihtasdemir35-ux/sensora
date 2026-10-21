package com.sensora.app.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.math.Stats
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*

private data class LightBand(val label: Int, val upper: Double)

private val BANDS = listOf(
    LightBand(R.string.light_ref_night, 1.0),
    LightBand(R.string.light_ref_living, 200.0),
    LightBand(R.string.light_ref_office, 500.0),
    LightBand(R.string.light_ref_reading, 1000.0),
    LightBand(R.string.light_ref_overcast, 10_000.0),
    LightBand(R.string.light_ref_sun, Double.MAX_VALUE),
)

@Composable
fun LightScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        val sensor = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_LIGHT) }
        if (sensor == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.light_unavailable)) }
            return@ToolScaffold
        }
        val sample by remember(sm) { SensorReaders.samples(sm, Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL) }
            .collectAsStateWithLifecycle(initialValue = null)
        var stats by remember { mutableStateOf(Stats.EMPTY) }
        val history = remember { mutableStateListOf<Float>() }
        val save = rememberMeasurementSaver()

        LaunchedEffect(sample) {
            val v = sample?.values?.firstOrNull()?.toDouble() ?: return@LaunchedEffect
            stats = stats.add(v)
            history.add(v.toFloat())
            if (history.size > 240) history.removeAt(0)
        }

        ScreenColumn(padding) {
            val lux = sample?.values?.firstOrNull()?.toDouble()
            if (lux == null) {
                WaitingCard()
                return@ScreenColumn
            }
            val quality = QualityRules.light(lux, sensor.maximumRange.toDouble())
            BigValue(fmt(lux, if (lux < 10) 1 else 0), "lx", caption = stringResource(R.string.hw_light))
            CenterRow { QualityBadge(quality) }
            if (quality != com.sensora.app.core.model.Quality.GOOD) WarningCard(stringResource(R.string.light_saturated))
            InfoCard {
                InfoRow(stringResource(R.string.stat_min), "${fmt(stats.min, 0)} lx")
                InfoRow(stringResource(R.string.stat_avg), "${fmt(stats.mean, 0)} lx")
                InfoRow(stringResource(R.string.stat_max), "${fmt(stats.max, 0)} lx")
                TextButton(onClick = { stats = Stats.EMPTY; history.clear() }) { Text(stringResource(R.string.reset)) }
            }
            LineGraph(history.toList(), minY = 0f)
            InfoCard {
                Text(stringResource(R.string.light_ref_title), style = MaterialTheme.typography.titleMedium)
                val current = BANDS.first { lux < it.upper }
                BANDS.forEach { band ->
                    Text(stringResource(band.label),
                        fontWeight = if (band == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (band == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(stringResource(R.string.light_hint), style = MaterialTheme.typography.bodySmall)
            SaveButton { note -> save(tool.record(lux, "lx", quality, "ambient_light_sensor", note = note)) }
        }
    }
}
