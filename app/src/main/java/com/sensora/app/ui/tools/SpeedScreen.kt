package com.sensora.app.ui.tools

import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.math.TrackAccumulator
import com.sensora.app.core.math.TrackPoint
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

@Composable
fun SpeedScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.speed_drive_warning))
            PermissionGate(LOCATION_PERMISSIONS, R.string.location_rationale) { SpeedContent(tool) }
        }
    }
}

@Composable
private fun SpeedContent(tool: Tool) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var running by rememberSaveable { mutableStateOf(false) }
    var track by remember { mutableStateOf(TrackAccumulator()) }
    var startedAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var lastFix by remember { mutableStateOf<Location?>(null) }
    var tick by remember { mutableStateOf(0) }
    var unit by rememberSaveable { mutableStateOf(0) }
    val save = rememberMeasurementSaver()

    if (running) {
        LaunchedEffect(Unit) {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SensorReaders.location(ctx, 1000).catch { }.collect { loc ->
                    lastFix = loc
                    track.add(TrackPoint(
                        lat = loc.latitude, lon = loc.longitude,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
                        timeMs = loc.elapsedRealtimeNanos / 1_000_000,
                        speedMps = if (loc.hasSpeed()) loc.speed else null,
                        altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                    ))
                    tick++
                }
            }
        }
        LaunchedEffect(Unit) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(1000) }
        }
    }

    val (factor, label) = when (unit) { 0 -> 3.6 to "km/h"; 1 -> 1.0 to "m/s"; else -> 2.236936 to "mph" }
    val elapsed = if (startedAt > 0) now - startedAt else 0L
    @Suppress("UNUSED_VARIABLE") val recompose = tick
    val fix = lastFix
    val ageMs = fix?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 } ?: Long.MAX_VALUE
    val quality = if (running) QualityRules.gps(fix?.takeIf { it.hasAccuracy() }?.accuracy, ageMs) else Quality.UNRELIABLE

    BigValue(track.currentSpeedMps?.let { fmt(it * factor, 1) } ?: "—", label, caption = stringResource(R.string.speed_current))
    CenterRow { QualityBadge(quality) }
    ChoiceChips(listOf("km/h", "m/s", "mph"), unit) { unit = it }
    if (running && quality == Quality.UNRELIABLE) WarningCard(stringResource(R.string.speed_low_accuracy))
    InfoCard {
        InfoRow(stringResource(R.string.speed_avg), track.averageSpeedMps(elapsed)?.let { "${fmt(it * factor, 1)} $label" } ?: "—")
        InfoRow(stringResource(R.string.speed_max), "${fmt(track.maxSpeedMps * factor, 1)} $label")
        InfoRow(stringResource(R.string.speed_distance), "${fmt(track.distanceM / 1000.0, 2)} km")
        InfoRow(stringResource(R.string.speed_time), formatDuration(elapsed))
        InfoRow(stringResource(R.string.speed_climb), "${fmt(track.ascentM, 0)} m")
        fix?.takeIf { it.hasAccuracy() }?.let { InfoRow(stringResource(R.string.gps_accuracy, "${fmt(it.accuracy.toDouble(), 0)} m"), "") }
    }
    Button(onClick = {
        if (running) {
            running = false
        } else {
            track = TrackAccumulator(); lastFix = null
            startedAt = SystemClock.elapsedRealtime(); now = startedAt
            running = true
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (running) R.string.speed_stop else R.string.speed_start)) }
    Text(stringResource(R.string.speed_foreground_note), style = MaterialTheme.typography.bodySmall)
    SaveButton(enabled = !running && track.acceptedFixes > 0) { note ->
        save(tool.record(track.distanceM / 1000.0, "km", quality, "gps_track",
            details = "avg ${track.averageSpeedMps(elapsed)?.let { fmt(it * 3.6, 1) } ?: "—"} km/h · max ${fmt(track.maxSpeedMps * 3.6, 1)} km/h · ${formatDuration(elapsed)}",
            note = note))
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
