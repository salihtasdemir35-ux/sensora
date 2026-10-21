package com.sensora.app.ui.health

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sensora.app.R
import com.sensora.app.core.audio.Channel
import com.sensora.app.core.audio.ToneSynth
import com.sensora.app.core.hearing.Ear
import com.sensora.app.core.hearing.EarResult
import com.sensora.app.core.hearing.HearingScreening
import com.sensora.app.core.hearing.HearingScreeningResult
import com.sensora.app.core.hearing.ThresholdSearch
import com.sensora.app.core.health.Urgency
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_USB_HEADSET, 26 /* TYPE_BLE_HEADSET (API 31) */,
)

private fun headphones(ctx: Context): String? =
    ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type in HEADPHONE_TYPES }?.let { it.productName?.toString()?.ifBlank { null } ?: "✓" }

/** Continuous float stream so presentation timing stays steady; levels are dBFS (not dB HL). */
private class HearingPlayer {
    val sampleRate = if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) >= 48_000) 48_000 else 44_100
    private val synth = ToneSynth(sampleRate)
    private val frames = sampleRate / 50
    private val buf = FloatArray(frames * 2)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT), frames * 8 * 4))
        .setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }

    val maxFrequency: Double get() = synth.nyquistHz * 0.9

    private suspend fun write(ms: Int) = withContext(Dispatchers.IO) {
        repeat(ms / 20) { if (isActive) { synth.fillFloat(buf, frames); track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) } }
    }

    /** Three 250 ms pulses (pulsed tones are easier to detect, as in clinical audiometry). */
    suspend fun tone(freq: Double, db: Int, ear: Ear) {
        synth.frequency = freq
        synth.channel = if (ear == Ear.RIGHT) Channel.RIGHT else Channel.LEFT
        repeat(3) { synth.targetAmplitude = 10.0.pow(db / 20.0); write(240); synth.targetAmplitude = 0.0; write(240) }
    }

    suspend fun silence(ms: Int) { synth.targetAmplitude = 0.0; write(ms) }

    fun release() { runCatching { track.stop() }; track.release() }
}

private enum class HPhase { SETUP, RUNNING, RESULT }

@Composable
fun HearingScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) { HearingContent(tool) }
    }
}

@Composable
private fun HearingContent(tool: Tool) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val save = rememberMeasurementSaver()
    var phase by remember { mutableStateOf(HPhase.SETUP) }
    var headset by remember { mutableStateOf(headphones(ctx)) }
    var volume by remember { mutableStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var tapped by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Pair<Ear, Int>?>(null) }
    var step by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(1) }
    var result by remember { mutableStateOf<HearingScreeningResult?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (phase == HPhase.RUNNING) phase = HPhase.SETUP }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { headset = headphones(ctx); volume = am.getStreamVolume(AudioManager.STREAM_MUSIC) }

    LaunchedEffect(phase) {
        if (phase != HPhase.RUNNING) return@LaunchedEffect
        val player = HearingPlayer()
        try {
            val freqs = HearingScreening.FREQUENCIES.filter { it < player.maxFrequency }
            val plan = listOf(Ear.RIGHT, Ear.LEFT).flatMap { e -> freqs.map { e to it } } + (Ear.RIGHT to 1000)
            total = plan.size
            val results = ArrayList<EarResult>()
            var retest: Int? = null
            var catches = 0; var falseAlarms = 0
            val rnd = Random(System.nanoTime())
            plan.forEachIndexed { i, (ear, f) ->
                step = i; current = ear to f
                val search = ThresholdSearch(-40)
                while (!search.done && phase == HPhase.RUNNING) {
                    delay(rnd.nextLong(1200, 2800))
                    // About one in eight presentations is silent to check for guessing.
                    if (rnd.nextDouble() < 0.12) {
                        tapped = false; catches++
                        player.silence(1440); player.silence(1200)
                        if (tapped) falseAlarms++
                        continue
                    }
                    tapped = false
                    player.tone(f.toDouble(), search.level, ear)
                    player.silence(1200)
                    search.respond(tapped)
                }
                if (phase != HPhase.RUNNING) return@LaunchedEffect
                if (i == plan.size - 1) retest = search.threshold else results.add(EarResult(ear, f, search.threshold))
            }
            val first = results.firstOrNull { it.ear == Ear.RIGHT && it.frequency == 1000 }?.threshold
            result = HearingScreening.evaluate(results, first to retest, catches, falseAlarms)
            phase = HPhase.RESULT
        } finally {
            player.release()
        }
    }

    when (phase) {
        HPhase.SETUP -> {
            WarningCard(stringResource(R.string.hs_disclaimer))
            InfoCard {
                Text(stringResource(R.string.hs_prepare_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.hs_steps), style = MaterialTheme.typography.bodyMedium)
            }
            InfoCard {
                InfoRow(stringResource(R.string.hs_headphones), headset ?: stringResource(R.string.hs_no_headphones))
                InfoRow(stringResource(R.string.hs_volume), "${volume * 100 / maxVol} %")
                OutlinedButton(onClick = {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol / 2, 0); volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                }) { Text(stringResource(R.string.hs_set_volume)) }
                Note(R.string.hs_volume_note)
            }
            if (headset == null) WarningCard(stringResource(R.string.hs_need_headphones), isError = true)
            Button(onClick = { result = null; phase = HPhase.RUNNING }, enabled = headset != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hs_start))
            }
        }
        HPhase.RUNNING -> {
            val (ear, f) = current ?: (Ear.RIGHT to 1000)
            Text(stringResource(R.string.hs_progress, step + 1, total), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (step + 1f) / total }, modifier = Modifier.fillMaxWidth())
            BigValue(stringResource(if (ear == Ear.RIGHT) R.string.ear_right else R.string.ear_left), "",
                caption = if (f >= 1000) "${f / 1000} kHz" else "$f Hz")
            Button(onClick = { tapped = true }, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Text(stringResource(R.string.hs_heard), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Note(R.string.hs_running_hint)
            TextButton(onClick = { phase = HPhase.SETUP }) { Text(stringResource(R.string.cancel)) }
        }
        HPhase.RESULT -> result?.let { r -> HearingResultCard(tool, r, headset, volume * 100 / maxVol, save) }
            ?.also { Button(onClick = { phase = HPhase.SETUP }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hs_again)) } }
    }
}

@Composable
private fun HearingResultCard(tool: Tool, r: HearingScreeningResult, headset: String?, volumePct: Int, save: (com.sensora.app.data.MeasurementRecord) -> Unit) {
    val red = Color(0xFFE53935); val blue = Color(0xFF1E88E5)
    val grid = MaterialTheme.colorScheme.outlineVariant
    ResultCard(stringResource(R.string.hs_result_title)) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val fs = HearingScreening.ORDERED
            fun x(f: Int) = size.width * (0.06f + 0.88f * (log10(f / 250.0) / log10(8000 / 250.0)).toFloat())
            fun y(db: Int) = size.height * ((db + 100) / 100f)   // −100 dBFS (better) at the top
            for (db in -100..0 step 20) drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), 1f)
            fs.forEach { drawLine(grid, Offset(x(it), 0f), Offset(x(it), size.height), 1f) }
            listOf(Ear.RIGHT to red, Ear.LEFT to blue).forEach { (ear, color) ->
                val pts = fs.mapNotNull { f -> r.results.firstOrNull { it.ear == ear && it.frequency == f }?.threshold?.let { Offset(x(f), y(it)) } }
                pts.zipWithNext { a, b -> drawLine(color, a, b, 3f) }
                pts.forEach { p ->
                    if (ear == Ear.RIGHT) drawCircle(color, 9f, p, style = Stroke(3f))
                    else { drawLine(color, p - Offset(8f, 8f), p + Offset(8f, 8f), 3f); drawLine(color, p - Offset(8f, -8f), p + Offset(8f, -8f), 3f) }
                }
            }
        }
        Note(R.string.hs_chart_legend)
        HearingScreening.ORDERED.forEach { f ->
            val rr = r.results.firstOrNull { it.ear == Ear.RIGHT && it.frequency == f } ?: return@forEach
            val ll = r.results.firstOrNull { it.ear == Ear.LEFT && it.frequency == f }
            val rightThreshold = rr.threshold?.let { "$it dBFS" }
                ?: stringResource(R.string.hs_no_response)

            val leftThreshold = ll?.threshold?.let { "$it dBFS" }
                ?: stringResource(R.string.hs_no_response)

            InfoRow(
                if (f >= 1000) "${f / 1000} kHz" else "$f Hz",
                "${stringResource(R.string.ear_right)} $rightThreshold · " +
                    "${stringResource(R.string.ear_left)} $leftThreshold"
                    )
        }
        if (r.asymmetric) Text(stringResource(R.string.hs_asymmetric, r.asymmetricFrequencies.joinToString { if (it >= 1000) "${it / 1000} kHz" else "$it Hz" }), fontWeight = FontWeight.SemiBold)
        else Text(stringResource(R.string.hs_symmetric), fontWeight = FontWeight.SemiBold)
        if (!r.reliable) WarningCard(stringResource(R.string.hs_unreliable))
        Note(R.string.hs_no_response_note)
        UrgencyBanner(if (r.asymmetric && r.reliable) Urgency.DISCUSS else Urgency.NORMAL)
        Note(R.string.hs_meaning)
        WarningCard(stringResource(R.string.au_sudden))
        InfoRow(stringResource(R.string.hs_headphones), headset ?: "—")
        InfoRow(stringResource(R.string.hs_volume), "$volumePct %")
        SaveButton { note ->
            val avg = r.results.mapNotNull { it.threshold }.average().takeIf { !it.isNaN() } ?: 0.0
            save(tool.record(avg, "dBFS", if (r.reliable) Quality.LIMITED else Quality.UNRELIABLE, "hughson_westlake_dbfs",
                details = r.results.joinToString(" ") { "${it.ear.name.first()}${it.frequency}:${it.threshold ?: "NR"}" } + " · $headset · vol $volumePct%", note = note))
        }
    }
}
