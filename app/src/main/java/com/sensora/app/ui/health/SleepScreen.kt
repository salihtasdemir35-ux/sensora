package com.sensora.app.ui.health

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sleep.AgeGroup
import com.sensora.app.core.sleep.SleepAnalysis
import com.sensora.app.core.sleep.SleepFlag
import com.sensora.app.sleep.SleepRecordingService
import com.sensora.app.sleep.SleepSession
import com.sensora.app.sleep.SleepStore
import com.sensora.app.ui.astro.formatDate
import com.sensora.app.ui.astro.formatDuration
import com.sensora.app.ui.astro.formatTime
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SleepScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.sleep_disclaimer))
            PermissionGate(arrayOf(Manifest.permission.RECORD_AUDIO), R.string.sleep_mic_rationale) { SleepContent(tool) }
        }
    }
}

@Composable
private fun SleepContent(tool: Tool) {
    val ctx = LocalContext.current
    val live by SleepRecordingService.state.collectAsStateWithLifecycle()
    var ageIndex by rememberSaveable { mutableStateOf(1) }
    var sessions by remember { mutableStateOf<List<SleepSession>>(emptyList()) }
    var selected by remember { mutableStateOf<Long?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SleepRecordingService.start(ctx) }

    LaunchedEffect(live.recording) {
        sessions = withContext(Dispatchers.IO) { SleepStore.list(ctx) }
        if (!live.recording && selected == null) selected = sessions.firstOrNull()?.startMs
    }

    if (live.recording) {
        InfoCard {
            Text(stringResource(R.string.sleep_recording), style = MaterialTheme.typography.titleMedium)
            BigValue(formatDuration(System.currentTimeMillis() - live.startMs), "", caption = stringResource(R.string.sleep_since, formatTime(live.startMs)))
            Note(R.string.sleep_recording_hint)
            if (live.error) WarningCard(stringResource(R.string.sound_mic_error), isError = true)
            Button(onClick = { SleepRecordingService.stop(ctx); selected = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sleep_stop))
            }
        }
        return
    }

    InfoCard {
        Text(stringResource(R.string.sleep_how_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sleep_steps), style = MaterialTheme.typography.bodyMedium)
    }
    ChoiceChips(listOf(stringResource(R.string.sleep_age_teen), stringResource(R.string.sleep_age_adult), stringResource(R.string.sleep_age_older)), ageIndex) { ageIndex = it }
    Button(onClick = {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else SleepRecordingService.start(ctx)
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_start)) }

    val age = AgeGroup.entries[ageIndex]
    sessions.firstOrNull { it.startMs == selected }?.let { s -> SleepReportCard(tool, s, age) }

    if (sessions.isNotEmpty()) {
        Text(stringResource(R.string.sleep_history), style = MaterialTheme.typography.titleMedium)
        sessions.take(14).forEach { s ->
            Card(Modifier.fillMaxWidth().clickable { selected = s.startMs }) {
                Text("${formatDate(s.startMs)} ${formatTime(s.startMs)} – ${formatTime(s.endMs)} · ${formatDuration(s.epochs.size * 60_000L)}",
                    modifier = Modifier.padding(12.dp), fontWeight = if (s.startMs == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SleepReportCard(tool: Tool, s: SleepSession, age: AgeGroup) {
    val save = rememberMeasurementSaver()
    if (s.epochs.size < 30) { WarningCard(stringResource(R.string.sleep_too_short)); return }
    val r = remember(s, age) { SleepAnalysis.report(s.epochs, age) }
    ResultCard(stringResource(R.string.sleep_report, formatDate(s.startMs))) {
        InfoRow(stringResource(R.string.sleep_in_bed), formatDuration(r.timeInBedMin * 60_000L))
        InfoRow(stringResource(R.string.sleep_estimated), formatDuration(r.totalSleepMin * 60_000L))
        InfoRow(stringResource(R.string.sleep_latency), r.onsetLatencyMin?.let { "$it min" } ?: "—")
        InfoRow(stringResource(R.string.sleep_efficiency), "${fmt(r.efficiency * 100, 0)} %")
        InfoRow(stringResource(R.string.sleep_awakenings), r.wakeBouts.toString())
        InfoRow(stringResource(R.string.sleep_snoring), "${r.snoreMin} min (${fmt(r.snoreMin * 100.0 / r.timeInBedMin, 0)} %)")
        InfoRow(stringResource(R.string.sleep_loud), r.loudEvents.toString())
        val activeColor = Color(0xFFFFA726); val stillColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val n = r.active.size
            val w = size.width / n
            for (i in 0 until n) {
                val h = if (r.active[i]) size.height else size.height * 0.4f
                drawRect(if (r.active[i]) activeColor else stillColor, Offset(i * w, size.height - h), Size(w.coerceAtLeast(1f), h))
            }
            s.epochs.forEachIndexed { i, e -> if (e.snore) drawRect(Color(0xFFE53935), Offset(i * w, 0f), Size(w.coerceAtLeast(1f), 6f)) }
        }
        Note(R.string.sleep_chart_legend)
        Text(stringResource(R.string.sleep_recommended, fmt(age.minHours, 0), fmt(age.maxHours, 0)), fontWeight = FontWeight.SemiBold)
        r.flags.forEach { f ->
            Text("• " + stringResource(when (f) {
                SleepFlag.SHORT -> R.string.sleep_flag_short
                SleepFlag.LONG -> R.string.sleep_flag_long
                SleepFlag.LOW_EFFICIENCY -> R.string.sleep_flag_efficiency
                SleepFlag.LONG_ONSET -> R.string.sleep_flag_onset
                SleepFlag.FREQUENT_SNORING -> R.string.sleep_flag_snoring
                SleepFlag.NOISY_ROOM -> R.string.sleep_flag_noise
            }), style = MaterialTheme.typography.bodyMedium)
        }
        if (r.flags.isEmpty()) Note(R.string.sleep_flag_none)
        UrgencyBanner(r.urgency)
        Note(R.string.sleep_apnea_signs)
        SaveButton { note -> save(tool.record(r.totalSleepMin / 60.0, "h", Quality.LIMITED, "phone_actigraphy",
            details = "eff ${fmt(r.efficiency * 100, 0)}% · snore ${r.snoreMin} min", note = note)) }
    }
}
