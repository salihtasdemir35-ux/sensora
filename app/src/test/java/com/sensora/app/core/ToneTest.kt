package com.sensora.app.core

import com.sensora.app.core.audio.Channel
import com.sensora.app.core.audio.ToneMath
import com.sensora.app.core.audio.ToneSynth
import com.sensora.app.core.audio.Waveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneTest {
    private fun render(synth: ToneSynth, seconds: Double): ShortArray {
        val frames = (synth.sampleRate * seconds).toInt()
        return ShortArray(frames * 2).also { synth.fill(it, frames) }
    }

    @Test fun logScale() {
        assertEquals(20.0, ToneMath.logFrequency(0.0), 1e-9)
        assertEquals(20_000.0, ToneMath.logFrequency(1.0), 1e-6)
        assertEquals(632.456, ToneMath.logFrequency(0.5), 1e-3)
        assertEquals(0.5, ToneMath.fractionOf(632.456), 1e-6)
    }

    @Test fun sweepEndpoints() {
        assertEquals(20.0, ToneMath.sweepFrequency(0.0, 10.0, 20.0, 20_000.0), 1e-9)
        assertEquals(20_000.0, ToneMath.sweepFrequency(10.0, 10.0, 20.0, 20_000.0), 1e-6)
        assertEquals(632.456, ToneMath.sweepFrequency(5.0, 10.0, 20.0, 20_000.0), 1e-3)
    }

    @Test fun volumeIsCapped() {
        assertEquals(ToneMath.dbToAmplitude(ToneMath.MAX_DB), ToneMath.dbToAmplitude(0.0), 0.0)
        assertEquals(0.5012, ToneMath.dbToAmplitude(-6.0), 1e-4)
    }

    @Test fun sineHasRequestedFrequency() {
        val s = ToneSynth(48_000).apply { frequency = 1000.0; targetAmplitude = 0.5 }
        val buf = render(s, 1.0)
        var crossings = 0
        for (i in 1 until buf.size / 2) {
            val a = buf[2 * (i - 1)]; val b = buf[2 * i]
            if ((a < 0) != (b < 0)) crossings++
        }
        assertEquals(2000.0, crossings.toDouble(), 4.0)
    }

    @Test fun fadeInPreventsClick() {
        val s = ToneSynth(48_000).apply { frequency = 1000.0; targetAmplitude = 1.0 }
        val buf = render(s, 0.05)
        assertTrue("first sample ${buf[0]}", abs(buf[0].toInt()) < 1000)
        assertTrue(buf.maxOf { abs(it.toInt()) } > 25_000)
    }

    @Test fun leftChannelOnly() {
        val s = ToneSynth(48_000).apply { channel = Channel.LEFT; targetAmplitude = 0.5; waveform = Waveform.SQUARE }
        val buf = render(s, 0.1)
        assertTrue((0 until buf.size / 2).all { buf[2 * it + 1].toInt() == 0 })
        assertTrue((0 until buf.size / 2).any { buf[2 * it].toInt() != 0 })
    }

    @Test fun noiseStaysInRange() {
        for (w in listOf(Waveform.WHITE_NOISE, Waveform.PINK_NOISE, Waveform.TRIANGLE, Waveform.SAWTOOTH)) {
            val s = ToneSynth(44_100).apply { waveform = w; targetAmplitude = ToneMath.dbToAmplitude(-6.0) }
            val buf = render(s, 0.5)
            assertTrue("$w", buf.maxOf { abs(it.toInt()) } <= 16_500)
            assertTrue("$w silent", buf.maxOf { abs(it.toInt()) } > 2_000)
        }
    }

    @Test fun frequencyClampedBelowNyquist() {
        val s = ToneSynth(44_100).apply { frequency = 30_000.0; targetAmplitude = 0.5 }
        render(s, 0.01) // must not alias wildly or throw
        assertEquals(22_050.0, s.nyquistHz, 0.0)
    }
}
