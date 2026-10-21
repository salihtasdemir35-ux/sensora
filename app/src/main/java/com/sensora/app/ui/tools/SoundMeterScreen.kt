package com.sensora.app.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.AudioMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.math.Series
import com.sensora.app.core.math.Stats
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlin.math.log10
import kotlin.math.pow

/** Session buffers: 10 Hz raw for 1 min and 1 Hz energy averages for 1 h. */
private class SoundSession {
    val raw = mutableStateListOf<Float>()
    val perSecond = mutableStateListOf<Float>()
    private val secondBuffer = ArrayList<Double>()
    private var energySum = 0.0
    private var energyCount = 0
    var stats by mutableStateOf(Stats.EMPTY)
    var current by mutableStateOf<Double?>(null)
    var clipping by mutableStateOf(false)

    fun add(dbfs: Double, clip: Boolean) {
        current = dbfs
        clipping = clip
        stats = stats.add(dbfs)
        energySum += 10.0.pow(dbfs / 10.0); energyCount++
        raw.add(dbfs.toFloat()); if (raw.size > 600) raw.removeAt(0)
        secondBuffer.add(dbfs)
        if (secondBuffer.size >= 10) {
            perSecond.add(AudioMath.energyAverage(secondBuffer).toFloat())
            secondBuffer.clear()
            if (perSecond.size > 3600) perSecond.removeAt(0)
        }
    }

    fun leq(): Double = if (energyCount == 0) Double.NaN else 10.0 * log10(energySum / energyCount)
    fun recentLeq(n: Int): Double = AudioMath.energyAverage(raw.takeLast(n).map { it.toDouble() })

    fun reset() {
        raw.clear(); perSecond.clear(); secondBuffer.clear()
        energySum = 0.0; energyCount = 0; stats = Stats.EMPTY
    }
}

@Composable
fun SoundMeterScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            PermissionGate(arrayOf(Manifest.permission.RECORD_AUDIO), R.string.sound_permission_rationale) {
                SoundMeterContent(tool)
            }
        }
    }
}

@Composable
private fun SoundMeterContent(tool: Tool) {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    val session = remember { SoundSession() }
    var error by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(prefs.soundCalibrationOffset) }
    var window by rememberSaveable { mutableStateOf(0) }
    var showCalibration by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val save = rememberMeasurementSaver()

    LaunchedEffect(Unit) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                SensorReaders.micFrames(ctx).collect { session.add(it.dbfs, it.clipping) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = true
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val cal = offset
        val calibrated = cal != null
        val unit = if (calibrated) "dB" else "dBFS"
        fun shown(v: Double) = if (cal != null) v + cal else v
        val lo = if (calibrated) 20f else -90f
        val hi = if (calibrated) 120f else 0f
        val quality = QualityRules.soundMeter(calibrated, session.clipping)

        if (error) WarningCard(stringResource(R.string.sound_mic_error), isError = true)
        val cur = session.current
        BigValue(cur?.let { fmt(shown(it), 1) } ?: "—", unit,
            caption = stringResource(if (calibrated) R.string.sound_calibrated_label else R.string.sound_uncalibrated_label))
        CenterRow { QualityBadge(quality) }
        if (cur != null) {
            LinearProgressIndicator(progress = { ((shown(cur).toFloat() - lo) / (hi - lo)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        }
        if (session.clipping) WarningCard(stringResource(R.string.sound_clipping))
        InfoCard {
            InfoRow(stringResource(R.string.stat_min), "${fmt(shown(session.stats.min), 1)} $unit")
            InfoRow(stringResource(R.string.sound_leq), "${fmt(shown(session.leq()), 1)} $unit")
            InfoRow(stringResource(R.string.stat_max), "${fmt(shown(session.stats.max), 1)} $unit")
        }
        ChoiceChips(listOf(stringResource(R.string.window_1m), stringResource(R.string.window_5m), stringResource(R.string.window_1h)), window) { window = it }
        val series = when (window) {
            0 -> session.raw.toList()
            1 -> session.perSecond.takeLast(300)
            else -> Series.downsample(session.perSecond.toList(), 600)
        }.map { it + (cal?.toFloat() ?: 0f) }
        LineGraph(series, minY = lo, maxY = hi)

        if (calibrated && cur != null) {
            val spl = shown(cur)
            Text(stringResource(when {
                spl < 40 -> R.string.sound_level_quiet
                spl < 70 -> R.string.sound_level_moderate
                spl < 85 -> R.string.sound_level_loud
                else -> R.string.sound_level_harmful
            }), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(if (calibrated) R.string.sound_calibrated else R.string.sound_uncalibrated),
            style = MaterialTheme.typography.bodySmall)
        CenterRow {
            OutlinedButton(onClick = { showCalibration = true }, enabled = cur != null) { Text(stringResource(R.string.calibrate)) }
            TextButton(onClick = { session.reset() }) { Text(stringResource(R.string.reset)) }
        }
        if (calibrated) {
            TextButton(onClick = { prefs.soundCalibrationOffset = null; offset = null }) {
                Text(stringResource(R.string.sound_clear_calibration))
            }
        }
        SaveButton(enabled = cur != null) { note ->
            save(tool.record(shown(session.leq()), unit, quality, if (calibrated) "mic_user_calibrated" else "mic_dbfs",
                details = "Leq · min ${fmt(shown(session.stats.min), 1)} · max ${fmt(shown(session.stats.max), 1)}", note = note))
        }
    }

    if (showCalibration) {
        NumberInputDialog(
            title = stringResource(R.string.sound_calibrate_title),
            message = stringResource(R.string.sound_calibrate_body),
            label = stringResource(R.string.sound_reference_db),
            initial = "",
            onDismiss = { showCalibration = false },
        ) { ref ->
            val recent = session.recentLeq(30)
            if (!recent.isNaN()) {
                val o = ref - recent
                prefs.soundCalibrationOffset = o
                offset = o
            }
        }
    }
}

