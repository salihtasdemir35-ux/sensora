package com.sensora.app.ui.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sensora.app.R
import com.sensora.app.core.audio.Channel
import com.sensora.app.core.audio.ToneMath
import com.sensora.app.core.audio.ToneSynth
import com.sensora.app.core.audio.Waveform
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

private const val AUTO_STOP_SEC = 180.0

/** Streams synthesised audio until [onBuffer] returns false or the coroutine is cancelled. */
private suspend fun playSynth(synth: ToneSynth, onBuffer: (elapsedSec: Double) -> Boolean) = withContext(Dispatchers.IO) {
    val sr = synth.sampleRate
    val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setBufferSizeInBytes(max(minBuf, 8192))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    val frames = sr / 50
    val buf = ShortArray(frames * 2)
    var written = 0L
    try {
        track.play()
        while (isActive) {
            if (!onBuffer(written.toDouble() / sr)) break
            synth.fill(buf, frames)
            track.write(buf, 0, buf.size)
            written += frames
        }
    } finally {
        // Short fade-out so stopping never clicks.
        synth.targetAmplitude = 0.0
        repeat(3) { synth.fill(buf, frames); track.write(buf, 0, buf.size) }
        runCatching { track.stop() }
        track.release()
    }
}

private fun formatHz(hz: Double): String =
    if (hz >= 1000) String.format(Locale.getDefault(), "%.2f kHz", hz / 1000) else String.format(Locale.getDefault(), "%.0f Hz", hz)

@Composable
fun ToneScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val owner = LocalLifecycleOwner.current
        val sampleRate = remember { if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) >= 48_000) 48_000 else 44_100 }
        val synth = remember { ToneSynth(sampleRate) }
        var mode by rememberSaveable { mutableStateOf(0) }           // 0 tone, 1 sweep, 2 left/right, 3 hearing range
        var playing by remember { mutableStateOf(false) }
        var waveIndex by rememberSaveable { mutableStateOf(0) }
        var freqFraction by rememberSaveable { mutableStateOf(ToneMath.fractionOf(1000.0).toFloat()) }
        var channelIndex by rememberSaveable { mutableStateOf(0) }
        var volumeDb by rememberSaveable { mutableStateOf(ToneMath.DEFAULT_DB.toFloat()) }
        var sweepDuration by rememberSaveable { mutableStateOf(1) }  // index into 10/30/60 s
        var loop by rememberSaveable { mutableStateOf(false) }
        var liveHz by remember { mutableStateOf(0.0) }
        var stereoLeft by remember { mutableStateOf(true) }
        var rangeResult by rememberSaveable { mutableStateOf<Double?>(null) }
        val save = rememberMeasurementSaver()
        val durations = listOf(10.0, 30.0, 60.0)

        // Parameters the playback thread reads every 20 ms.
        synth.waveform = if (mode == 0) Waveform.entries[waveIndex] else Waveform.SINE
        synth.channel = if (mode == 0) Channel.entries[channelIndex] else synth.channel
        val amplitude by rememberUpdatedState(ToneMath.dbToAmplitude(volumeDb.toDouble()))
        // Never resume sound by surprise: leaving the screen stops playback.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playing = false }

        LaunchedEffect(playing, mode) {
            if (!playing) return@LaunchedEffect
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playSynth(synth) { t ->
                    synth.targetAmplitude = amplitude
                    when (mode) {
                        0 -> synth.frequency = ToneMath.logFrequency(freqFraction.toDouble())
                        1 -> {
                            val d = durations[sweepDuration]
                            val tt = if (loop) t % d else t
                            if (!loop && t > d) { playing = false; return@playSynth false }
                            synth.channel = Channel.BOTH
                            synth.frequency = ToneMath.sweepFrequency(tt, d, ToneMath.MIN_HZ, minOf(ToneMath.MAX_HZ, synth.nyquistHz * 0.95))
                        }
                        2 -> {
                            synth.frequency = 1000.0
                            val left = (t.toInt() % 2) == 0
                            synth.channel = if (left) Channel.LEFT else Channel.RIGHT
                            stereoLeft = left
                        }
                        else -> {
                            synth.channel = Channel.BOTH
                            synth.frequency = ToneMath.sweepFrequency(t, 60.0, 8_000.0, minOf(20_000.0, synth.nyquistHz * 0.95))
                            if (t > 60.0) { playing = false; return@playSynth false }
                        }
                    }
                    liveHz = synth.frequency
                    if (t > AUTO_STOP_SEC) { playing = false; false } else true
                }
            }
        }

        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.tone_safety))
            ChoiceChips(listOf(stringResource(R.string.tone_mode_tone), stringResource(R.string.tone_mode_sweep),
                stringResource(R.string.tone_mode_stereo), stringResource(R.string.tone_mode_range)), mode) {
                playing = false; mode = it
            }

            when (mode) {
                0 -> {
                    ChoiceChips(listOf(stringResource(R.string.wave_sine), stringResource(R.string.wave_square), stringResource(R.string.wave_triangle),
                        stringResource(R.string.wave_saw), stringResource(R.string.wave_white), stringResource(R.string.wave_pink)), waveIndex) { waveIndex = it }
                    val noise = waveIndex >= 4
                    if (!noise) {
                        BigValue(formatHz(ToneMath.logFrequency(freqFraction.toDouble())), "", caption = stringResource(R.string.tone_frequency))
                        Slider(value = freqFraction, onValueChange = { freqFraction = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(100.0, 440.0, 1000.0, 10_000.0).forEach { hz ->
                                AssistChip(onClick = { freqFraction = ToneMath.fractionOf(hz).toFloat() }, label = { Text(formatHz(hz)) })
                            }
                        }
                    }
                    ChoiceChips(listOf(stringResource(R.string.tone_channel_both), stringResource(R.string.tone_channel_left),
                        stringResource(R.string.tone_channel_right)), channelIndex) { channelIndex = it }
                }
                1 -> {
                    BigValue(if (playing) formatHz(liveHz) else "20 Hz → 20 kHz", "", caption = stringResource(R.string.tone_mode_sweep))
                    Text(stringResource(R.string.tone_sweep_duration))
                    ChoiceChips(listOf("10 s", "30 s", "60 s"), sweepDuration) { sweepDuration = it; playing = false }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = loop, onCheckedChange = { loop = it })
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.tone_loop))
                    }
                }
                2 -> {
                    BigValue(if (playing) stringResource(if (stereoLeft) R.string.tone_stereo_now_left else R.string.tone_stereo_now_right) else "L / R", "")
                    Text(stringResource(R.string.tone_stereo_hint), style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Text(stringResource(R.string.tone_range_hint), style = MaterialTheme.typography.bodyMedium)
                    if (playing) BigValue(formatHz(liveHz), "", caption = stringResource(R.string.tone_frequency))
                    rangeResult?.let { r ->
                        InfoCard {
                            Text(stringResource(R.string.tone_range_result, formatHz(r)), fontWeight = FontWeight.SemiBold)
                            QualityBadge(Quality.UNRELIABLE)
                            SaveButton { note -> save(tool.record(r, "Hz", Quality.UNRELIABLE, "rising_tone_self_check", note = note)) }
                        }
                    }
                    Text(stringResource(R.string.tone_range_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.tone_range_limit, formatHz(synth.nyquistHz)), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(stringResource(R.string.tone_volume, String.format(Locale.getDefault(), "%.0f", volumeDb)))
            Slider(value = volumeDb, onValueChange = { volumeDb = it },
                valueRange = ToneMath.MIN_DB.toFloat()..ToneMath.MAX_DB.toFloat())
            if (volumeDb > ToneMath.LOUD_DB) WarningCard(stringResource(R.string.tone_loud_warning))

            if (mode == 3 && playing) {
                Button(onClick = { rangeResult = liveHz; playing = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tone_range_stop))
                }
            } else {
                Button(onClick = { if (!playing) rangeResult = if (mode == 3) null else rangeResult; playing = !playing },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (playing) R.string.tone_stop else R.string.tone_play))
                }
            }
            Text(stringResource(R.string.tone_auto_stop), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tone_device_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
