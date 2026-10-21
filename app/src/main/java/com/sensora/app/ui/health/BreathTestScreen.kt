package com.sensora.app.ui.health

import android.Manifest
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sensora.app.R
import com.sensora.app.core.health.BlowDetector
import com.sensora.app.core.health.BlowIssue
import com.sensora.app.core.health.BlowTrial
import com.sensora.app.core.health.BreathSession
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.takeWhile

private enum class Phase { IDLE, QUIET, INHALE, BLOW }

private const val FRAME_MS = 50
private const val FRAME_SEC = FRAME_MS / 1000.0

@Composable
fun BreathTestScreen(tool: Tool, onBack: () -> Unit, onOpenTool: (String) -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.breath_disclaimer))
            PermissionGate(arrayOf(Manifest.permission.RECORD_AUDIO), R.string.sound_permission_rationale) {
                BreathContent(tool)
            }
            OutlinedButton(onClick = { onOpenTool("results") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.breath_open_interpreter))
            }
        }
    }
}

@Composable
private fun BreathContent(tool: Tool) {
    val ctx = LocalContext.current
    val save = rememberMeasurementSaver()
    var child by rememberSaveable { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var attempt by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(-90.0) }
    var countdown by remember { mutableStateOf(3) }
    var blowSec by remember { mutableStateOf(0.0) }
    var problem by remember { mutableStateOf<Int?>(null) }
    val trials = remember { mutableStateListOf<BlowTrial>() }

    // Never keep the microphone open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { phase = Phase.IDLE }

    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        problem = null
        phase = Phase.QUIET
        val quiet = ArrayList<Double>()
        var floor = -70.0
        var inhaleFrames = 0
        var detector: BlowDetector? = null
        try {
            SensorReaders.micFrames(ctx, FRAME_MS).takeWhile { phase != Phase.IDLE }.collect { f ->
                level = f.dbfs
                when (phase) {
                    Phase.QUIET -> {
                        quiet.add(f.dbfs)
                        if (quiet.size >= 30) {
                            floor = quiet.sorted()[quiet.size / 2]
                            if (floor > -35.0) { problem = R.string.breath_too_noisy; phase = Phase.IDLE }
                            else { phase = Phase.INHALE; inhaleFrames = 0 }
                        }
                    }
                    Phase.INHALE -> {
                        inhaleFrames++
                        countdown = 3 - (inhaleFrames * FRAME_MS) / 1000
                        if (inhaleFrames * FRAME_MS >= 3000) {
                            detector = BlowDetector(FRAME_SEC, floor, if (child) 3.0 else 6.0)
                            phase = Phase.BLOW
                        }
                    }
                    Phase.BLOW -> {
                        val d = detector!!
                        val done = d.add(f.dbfs, f.clipping)
                        blowSec = d.elapsedBlowSec
                        if (done) { trials.add(d.result()); phase = Phase.IDLE }
                    }
                    Phase.IDLE -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = R.string.sound_mic_error
            phase = Phase.IDLE
        }
    }

    when (phase) {
        Phase.IDLE -> {
            InfoCard {
                Text(stringResource(R.string.breath_how_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.breath_steps), style = MaterialTheme.typography.bodyMedium)
            }
            ChoiceChips(listOf(stringResource(R.string.breath_adult), stringResource(R.string.breath_child)), if (child) 1 else 0) {
                child = it == 1; trials.clear()
            }
            problem?.let { WarningCard(stringResource(it), isError = true) }
            val complete = BreathSession.complete(trials)
            Button(onClick = { attempt++ }, enabled = !complete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.breath_start, trials.size + 1))
            }
        }
        Phase.QUIET -> BigValue(stringResource(R.string.breath_quiet), "")
        Phase.INHALE -> BigValue(countdown.coerceAtLeast(1).toString(), "", caption = stringResource(R.string.breath_inhale))
        Phase.BLOW -> {
            BigValue(fmt(blowSec, 1), "s", caption = stringResource(R.string.breath_blow_now))
            LinearProgressIndicator(progress = { ((level + 80) / 80).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { phase = Phase.IDLE }) { Text(stringResource(R.string.cancel)) }
        }
    }

    trials.forEachIndexed { i, t -> TrialCard(i + 1, t) }

    if (trials.isNotEmpty() && phase == Phase.IDLE) {
        val acceptable = trials.count { it.acceptable }
        val repeatable = BreathSession.repeatable(trials)
        val best = BreathSession.best(trials)!!
        val quality = if (acceptable >= 3 && repeatable) Quality.LIMITED else Quality.UNRELIABLE
        InfoCard {
            Text(stringResource(R.string.breath_summary), style = MaterialTheme.typography.titleMedium)
            InfoRow(stringResource(R.string.breath_acceptable), "$acceptable / 3")
            InfoRow(stringResource(R.string.breath_repeatable), stringResource(if (repeatable) R.string.yes else R.string.no))
            InfoRow(stringResource(R.string.breath_best_duration), "${fmt(best.durationSec, 1)} s")
            InfoRow(stringResource(R.string.breath_best_strength), "+${fmt(best.peakAboveFloorDb, 0)} dB")
            QualityBadge(quality)
            Text(stringResource(when {
                acceptable >= 3 && repeatable -> R.string.breath_session_ok
                trials.size >= 8 -> R.string.breath_session_max
                else -> R.string.breath_session_more
            }), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.breath_trend_note), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { trials.clear(); problem = null }) { Text(stringResource(R.string.reset)) }
        }
        SaveButton { note ->
            save(tool.record(best.durationSec, "s", quality, "mic_blow_ats_ers_procedure",
                details = "peak +${fmt(best.peakAboveFloorDb, 0)} dB · $acceptable/3 · repeatable=$repeatable", note = note))
        }
    }
}

@Composable
private fun TrialCard(n: Int, t: BlowTrial) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.breath_trial, n), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                QualityBadge(if (t.acceptable) Quality.GOOD else Quality.UNRELIABLE)
            }
            if (BlowIssue.NO_BLOW !in t.issues) {
                Text("${fmt(t.durationSec, 1)} s · +${fmt(t.peakAboveFloorDb, 0)} dB · ${stringResource(R.string.breath_ttp)} ${fmt(t.timeToPeakSec, 2)} s",
                    style = MaterialTheme.typography.bodySmall)
            }
            t.issues.forEach { issue ->
                Text("• " + stringResource(when (issue) {
                    BlowIssue.NO_BLOW -> R.string.breath_issue_noblow
                    BlowIssue.SLOW_START -> R.string.breath_issue_slow
                    BlowIssue.TOO_SHORT -> R.string.breath_issue_short
                    BlowIssue.INTERRUPTED -> R.string.breath_issue_interrupted
                    BlowIssue.CLIPPED -> R.string.breath_issue_clipped
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
