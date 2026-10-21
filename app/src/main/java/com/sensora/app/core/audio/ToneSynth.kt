package com.sensora.app.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Waveform { SINE, SQUARE, TRIANGLE, SAWTOOTH, WHITE_NOISE, PINK_NOISE }
enum class Channel { BOTH, LEFT, RIGHT }

object ToneMath {
    const val MIN_HZ = 20.0
    const val MAX_HZ = 20_000.0
    /** Safe default level and hard ceiling, in dB relative to full scale. */
    const val DEFAULT_DB = -24.0
    const val MIN_DB = -60.0
    const val MAX_DB = -3.0
    const val LOUD_DB = -12.0

    /** Slider position 0..1 to frequency on a logarithmic scale (equal steps per octave). */
    fun logFrequency(fraction: Double, minHz: Double = MIN_HZ, maxHz: Double = MAX_HZ): Double =
        minHz * (maxHz / minHz).pow(fraction.coerceIn(0.0, 1.0))

    fun fractionOf(hz: Double, minHz: Double = MIN_HZ, maxHz: Double = MAX_HZ): Double =
        (ln(hz / minHz) / ln(maxHz / minHz)).coerceIn(0.0, 1.0)

    /** Exponential sweep: the same time is spent on every octave. */
    fun sweepFrequency(tSec: Double, durationSec: Double, startHz: Double, endHz: Double): Double =
        startHz * (endHz / startHz).pow((tSec / durationSec).coerceIn(0.0, 1.0))

    fun dbToAmplitude(db: Double): Double = 10.0.pow(db.coerceIn(MIN_DB, MAX_DB) / 20.0)
}

/**
 * Click-free stereo synthesiser. Parameters may be changed from another thread between buffers;
 * amplitude changes are smoothed over ~10 ms and frequency changes keep phase continuity.
 */
class ToneSynth(val sampleRate: Int, seed: Int = 1) {
    @Volatile var waveform = Waveform.SINE
    @Volatile var frequency = 1000.0
    @Volatile var targetAmplitude = 0.0
    @Volatile var channel = Channel.BOTH

    private var amplitude = 0.0
    private var phase = 0.0
    private val rng = Random(seed)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private val smoothing = 1.0 - kotlin.math.exp(-1.0 / (0.01 * sampleRate))

    val nyquistHz: Double get() = sampleRate / 2.0

    private fun pink(white: Double): Double {
        // Paul Kellet's economy pink-noise filter.
        b0 = 0.99765 * b0 + white * 0.0990460
        b1 = 0.96300 * b1 + white * 0.2965164
        b2 = 0.57000 * b2 + white * 1.0526913
        return ((b0 + b1 + b2 + white * 0.1848) * 0.25).coerceIn(-1.0, 1.0)
    }

    /** Float output (−1…1) for very quiet test tones, where 16-bit samples would distort. */
    fun fillFloat(out: FloatArray, frames: Int) {
        val f = frequency.coerceIn(1.0, nyquistHz * 0.98)
        val step = f / sampleRate
        val ch = channel
        for (i in 0 until frames) {
            amplitude += (targetAmplitude - amplitude) * smoothing
            val v = (sin(2 * PI * phase) * amplitude).toFloat()
            phase += step
            if (phase >= 1.0) phase -= floor(phase)
            out[2 * i] = if (ch == Channel.RIGHT) 0f else v
            out[2 * i + 1] = if (ch == Channel.LEFT) 0f else v
        }
    }

    /** Fills [frames] stereo frames (interleaved L, R) into [out]. */
    fun fill(out: ShortArray, frames: Int) {
        val f = frequency.coerceIn(1.0, nyquistHz * 0.98)
        val step = f / sampleRate
        val wave = waveform
        val ch = channel
        for (i in 0 until frames) {
            amplitude += (targetAmplitude - amplitude) * smoothing
            val s = when (wave) {
                Waveform.SINE -> sin(2 * PI * phase)
                Waveform.SQUARE -> if (phase < 0.5) 0.8 else -0.8
                Waveform.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
                Waveform.SAWTOOTH -> 2.0 * phase - 1.0
                Waveform.WHITE_NOISE -> rng.nextDouble() * 2.0 - 1.0
                Waveform.PINK_NOISE -> pink(rng.nextDouble() * 2.0 - 1.0)
            }
            phase += step
            if (phase >= 1.0) phase -= floor(phase)
            val v = (s * amplitude * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            out[2 * i] = if (ch == Channel.RIGHT) 0 else v
            out[2 * i + 1] = if (ch == Channel.LEFT) 0 else v
        }
    }
}
