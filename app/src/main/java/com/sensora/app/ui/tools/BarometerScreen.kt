package com.sensora.app.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.AtmosphereMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.flow.catch

@Composable
fun BarometerScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        val hasBarometer = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null }
        ScreenColumn(padding) {
            if (hasBarometer) BarometerSection(tool, sm) else UnavailableCard(stringResource(R.string.baro_unavailable))
            GpsAltitudeSection()
        }
    }
}

@Composable
private fun BarometerSection(tool: Tool, sm: SensorManager) {
    val sample by remember(sm) { SensorReaders.samples(sm, Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL) }
        .collectAsStateWithLifecycle(initialValue = null)
    var seaLevel by rememberSaveable { mutableStateOf(AtmosphereMath.STANDARD_SEA_LEVEL_HPA) }
    var calibratedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var zeroAltitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var unit by rememberSaveable { mutableStateOf(0) }
    var askKnown by remember { mutableStateOf(false) }
    val trend = remember { mutableStateListOf<Float>() }
    var lastTrendMs by remember { mutableStateOf(0L) }
    val save = rememberMeasurementSaver()

    LaunchedEffect(sample) {
        val p = sample?.values?.firstOrNull() ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrendMs >= 1000) {
            trend.add(p); lastTrendMs = now
            if (trend.size > 1800) trend.removeAt(0)
        }
    }

    val p = sample?.values?.firstOrNull()?.toDouble()
    if (p == null) {
        WaitingCard()
        return
    }
    val altitude = AtmosphereMath.altitudeFromPressure(p, seaLevel)
    val altQuality = QualityRules.barometricAltitude(calibratedAt?.let { System.currentTimeMillis() - it })
    val pressureText = when (unit) {
        0 -> fmt(p, 1) to "hPa"
        1 -> fmt(AtmosphereMath.hPaToInHg(p), 2) to "inHg"
        else -> fmt(AtmosphereMath.hPaToMmHg(p), 1) to "mmHg"
    }
    BigValue(pressureText.first, pressureText.second, caption = stringResource(R.string.baro_pressure))
    CenterRow { QualityBadge(Quality.GOOD) }
    ChoiceChips(listOf("hPa", "inHg", "mmHg"), unit) { unit = it }
    InfoCard {
        InfoRow(stringResource(R.string.baro_altitude_std), "${fmt(altitude, 0)} m")
        zeroAltitude?.let { InfoRow(stringResource(R.string.baro_altitude_rel), String.format(java.util.Locale.getDefault(), "%+.1f m", altitude - it)) }
        CenterRow { QualityBadge(altQuality) }
    }
    CenterRow {
        OutlinedButton(onClick = { zeroAltitude = altitude }) { Text(stringResource(R.string.baro_set_zero)) }
        OutlinedButton(onClick = { askKnown = true }) { Text(stringResource(R.string.baro_set_known)) }
    }
    Text(stringResource(R.string.baro_trend), style = MaterialTheme.typography.titleSmall)
    LineGraph(trend.toList())
    Text(stringResource(R.string.baro_weather_note), style = MaterialTheme.typography.bodySmall)
    SaveButton { note ->
        save(tool.record(p, "hPa", Quality.GOOD, "barometer",
            details = "alt ${fmt(altitude, 0)} m (ref ${fmt(seaLevel, 1)} hPa)", note = note))
    }

    if (askKnown) {
        NumberInputDialog(
            title = stringResource(R.string.baro_set_known), message = null,
            label = stringResource(R.string.baro_known_altitude), initial = "",
            onDismiss = { askKnown = false },
        ) { known ->
            seaLevel = AtmosphereMath.seaLevelPressure(p, known)
            calibratedAt = System.currentTimeMillis()
        }
    }
}

private fun gpsAltitude(loc: Location): Pair<Double, Boolean>? {
    if (Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude()) return loc.mslAltitudeMeters to true
    return if (loc.hasAltitude()) loc.altitude to false else null
}

@Composable
private fun GpsAltitudeSection() {
    var show by rememberSaveable { mutableStateOf(false) }
    InfoCard {
        Text(stringResource(R.string.gps_altitude), style = MaterialTheme.typography.titleMedium)
        if (!show) {
            OutlinedButton(onClick = { show = true }) { Text(stringResource(R.string.gps_show)) }
        } else {
            PermissionGate(LOCATION_PERMISSIONS, R.string.location_rationale) {
                val ctx = LocalContext.current
                val loc by remember { SensorReaders.location(ctx, 2000).catch { } }.collectAsStateWithLifecycle(initialValue = null)
                val l = loc
                val alt = l?.let { gpsAltitude(it) }
                if (l == null || alt == null) {
                    Text(stringResource(R.string.gps_waiting))
                } else {
                    InfoRow(stringResource(if (alt.second) R.string.gps_altitude_msl else R.string.gps_altitude_ellipsoid), "${fmt(alt.first, 0)} m")
                    if (l.hasVerticalAccuracy()) Text(stringResource(R.string.gps_accuracy, "${fmt(l.verticalAccuracyMeters.toDouble(), 0)} m"))
                }
            }
        }
    }
}
