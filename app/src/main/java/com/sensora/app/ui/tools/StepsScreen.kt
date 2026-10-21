package com.sensora.app.ui.tools

import android.Manifest
import android.hardware.Sensor
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.core.steps.AccelStepDetector
import com.sensora.app.core.steps.StepDayTracker
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import java.text.NumberFormat
import java.time.LocalDate

@Composable
fun StepsScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        val hasCounter = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
        val hasAccel = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
        val perms = if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyArray()
        ScreenColumn(padding) {
            when {
                hasCounter -> PermissionGate(perms, R.string.steps_permission) { HardwareSteps(tool) }
                hasAccel -> FallbackSteps(tool)
                else -> UnavailableCard(stringResource(R.string.accel_unavailable))
            }
        }
    }
}

@Composable
private fun HardwareSteps(tool: Tool) {
    val prefs = LocalContext.current.sensoraApp.prefs
    val sm = rememberSensorManager()
    val sample by remember(sm) { SensorReaders.samples(sm, Sensor.TYPE_STEP_COUNTER) }.collectAsStateWithLifecycle(initialValue = null)
    val today = LocalDate.now().toString()
    var todaySteps by remember { mutableStateOf(prefs.dailySteps()[today] ?: 0L) }
    var daily by remember { mutableStateOf(prefs.dailySteps()) }

    LaunchedEffect(sample) {
        val counter = sample?.values?.firstOrNull()?.toLong() ?: return@LaunchedEffect
        val upd = StepDayTracker.update(prefs.stepState, counter, LocalDate.now().toString())
        prefs.stepState = upd.state
        upd.closedDay?.let { (day, steps) -> prefs.putDailySteps(day, steps) }
        prefs.putDailySteps(upd.state.day ?: today, upd.todaySteps)
        todaySteps = upd.todaySteps
        daily = prefs.dailySteps()
    }
    StepsDashboard(tool, todaySteps, Quality.GOOD, "step_counter_sensor", daily)
    Text(stringResource(R.string.steps_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FallbackSteps(tool: Tool) {
    val sm = rememberSensorManager()
    val detector = remember { AccelStepDetector() }
    var steps by rememberSaveable { mutableStateOf(0L) }
    val sample by remember(sm) { SensorReaders.samples(sm, Sensor.TYPE_ACCELEROMETER, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
        .collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(sample) {
        val s = sample ?: return@LaunchedEffect
        if (detector.onSample(s.values[0], s.values[1], s.values[2], s.timestampNs / 1_000_000)) steps++
    }
    WarningCard(stringResource(R.string.steps_fallback))
    StepsDashboard(tool, steps, Quality.LIMITED, "accelerometer_peaks", emptyMap())
}

@Composable
private fun StepsDashboard(tool: Tool, steps: Long, quality: Quality, method: String, daily: Map<String, Long>) {
    val prefs = LocalContext.current.sensoraApp.prefs
    var goal by remember { mutableStateOf(prefs.stepGoal) }
    var height by remember { mutableStateOf(prefs.heightCm) }
    var editGoal by remember { mutableStateOf(false) }
    var editHeight by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(0) }
    val save = rememberMeasurementSaver()
    val nf = remember { NumberFormat.getIntegerInstance() }
    val distanceKm = steps * StepDayTracker.stepLengthM(height) / 1000.0

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing(steps.toFloat() / goal.coerceAtLeast(1), Modifier.size(220.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(nf.format(steps), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.steps_today), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.steps_goal, goal), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    CenterRow { QualityBadge(quality) }
    InfoCard {
        InfoRow(stringResource(R.string.steps_distance), "≈ ${fmt(distanceKm, 2)} km")
        InfoRow(stringResource(R.string.steps_height), "$height cm")
    }
    CenterRow {
        OutlinedButton(onClick = { editGoal = true }) { Text(stringResource(R.string.steps_set_goal)) }
        OutlinedButton(onClick = { editHeight = true }) { Text(stringResource(R.string.steps_height)) }
    }
    if (daily.isNotEmpty()) {
        ChoiceChips(listOf(stringResource(R.string.steps_week), stringResource(R.string.steps_month)), range) { range = it }
        val days = if (range == 0) 7 else 30
        val now = LocalDate.now()
        val values = (days - 1 downTo 0).map { (daily[now.minusDays(it.toLong()).toString()] ?: 0L).toFloat() }
        BarChart(values, goal.toFloat())
    }
    SaveButton { note ->
        save(tool.record(steps.toDouble(), "steps", quality, method, details = "≈ ${fmt(distanceKm, 2)} km", note = note))
    }

    if (editGoal) NumberInputDialog(stringResource(R.string.steps_set_goal), null, stringResource(R.string.steps_set_goal),
        goal.toString(), { editGoal = false }) { v -> val g = v.toInt().coerceIn(100, 100_000); goal = g; prefs.stepGoal = g }
    if (editHeight) NumberInputDialog(stringResource(R.string.steps_height), null, "cm",
        height.toString(), { editHeight = false }) { v -> val h = v.toInt().coerceIn(50, 250); height = h; prefs.heightCm = h }
}
