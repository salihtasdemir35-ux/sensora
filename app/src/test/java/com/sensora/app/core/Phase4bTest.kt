package com.sensora.app.core

import com.sensora.app.core.health.PulseAnalyzer
import com.sensora.app.core.model.Quality
import com.sensora.app.core.quake.Fft
import com.sensora.app.core.quake.Intensity
import com.sensora.app.core.quake.QuakeParsers
import com.sensora.app.core.quake.QuakeSource
import com.sensora.app.core.quake.VibrationBuffer
import com.sensora.app.core.sleep.AgeGroup
import com.sensora.app.core.sleep.SleepAnalysis
import com.sensora.app.core.sleep.SleepEpoch
import com.sensora.app.core.sleep.SleepFlag
import com.sensora.app.core.sleep.SleepMath
import com.sensora.app.core.sleep.SoundEventDetector
import com.sensora.app.core.util.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class JsonAndQuakeTest {
    @Test fun miniJson() {
        val v = MiniJson.parse("""{"a":[1,2.5,-3e2],"b":"x\"y\u00e7","c":null,"d":true}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, -300.0), v["a"]); assertEquals("x\"yç", v["b"]); assertNull(v["c"]); assertEquals(true, v["d"])
    }

    @Test fun afadParsingTurkeyTime() {
        val json = """[{"rms":"0.5","eventID":"725480","location":"Korkuteli (Antalya)","latitude":"37.0612","longitude":"30.1934","depth":"7.2","type":"ML","magnitude":"2.6","country":"Türkiye","province":"Antalya","district":"Korkuteli","date":"2026-08-13T11:54:06"}]"""
        val q = QuakeParsers.afad(json).single()
        assertEquals(QuakeSource.AFAD, q.source); assertEquals(2.6, q.magnitude, 0.0); assertEquals("Korkuteli (Antalya)", q.place)
        assertEquals(Instant.parse("2026-08-13T08:54:06Z").toEpochMilli(), q.timeMs)   // UTC+3
    }

    @Test fun usgsParsing() {
        val json = """{"type":"FeatureCollection","features":[{"type":"Feature","id":"us7000abcd","properties":{"mag":4.6,"place":"south of the Fiji Islands","time":1788306391673},"geometry":{"type":"Point","coordinates":[-179.6367,-25.2674,534.03]}}]}"""
        val q = QuakeParsers.usgs(json).single()
        assertEquals(534.03, q.depthKm, 1e-9); assertEquals(-25.2674, q.lat, 1e-9); assertEquals(1788306391673L, q.timeMs)
    }

    @Test fun malformedItemsAreSkipped() {
        assertEquals(0, QuakeParsers.afad("""[{"magnitude":"x"},{}]""").size)
        assertEquals(0, QuakeParsers.usgs("""{"features":[{"properties":{}}]}""").size)
    }

    @Test fun afadUrlAlwaysHasSeconds() {
        val url = QuakeParsers.afadUrl(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), 24, 3.0)
        assertTrue(url, url.contains("start=2026-10-19T12:00:00&end=2026-10-20T12:00:00"))
    }

    @Test fun waldIntensity() {
        assertEquals(5.66, Intensity.mmiFromPga(1.0), 0.01)    // 100 cm/s²
        assertEquals(3.2, Intensity.mmiFromPga(0.1), 0.01)     // 10 cm/s²
        assertEquals("VI", Intensity.roman(6.2))
    }

    @Test fun fftFindsFrequency() {
        val fs = 200.0; val n = 512
        val sig = DoubleArray(n) { 0.5 * sin(2 * PI * 12.0 * it / fs) + 0.05 * sin(2 * PI * 40.0 * it / fs) }
        val spec = Fft.amplitudeSpectrum(sig)
        val p = Fft.dominant(spec, fs, n)
        assertEquals(12.0, p.frequencyHz, fs / n)
        assertEquals(0.5, p.amplitude, 0.1)
    }

    @Test fun vibrationBuffer() {
        val b = VibrationBuffer(100)
        for (i in 0 until 150) b.add(i * 10_000_000L, 0f, 0f, if (i == 120) 3f else 0.1f)
        assertEquals(100, b.count); assertEquals(3.0, b.peak, 1e-6); assertEquals(100.0, b.sampleRate(), 0.5)
    }
}

class PulseTest {
    private fun synth(bpm: Double, seconds: Double, noise: Double, seed: Int = 1): Pair<DoubleArray, DoubleArray> {
        val r = Random(seed); val t = ArrayList<Double>(); val v = ArrayList<Double>()
        var time = 0.0
        while (time < seconds) {
            val f = bpm / 60.0
            v.add(150 - 2.0 * sin(2 * PI * f * time) - 0.6 * sin(4 * PI * f * time) + 4.0 * time / seconds + r.nextDouble(-noise, noise))
            t.add(time); time += 1.0 / 30.0 + r.nextDouble(-0.004, 0.004)
        }
        return t.toDoubleArray() to v.toDoubleArray()
    }

    @Test fun detectsHeartRates() {
        for (bpm in listOf(48.0, 72.0, 110.0, 150.0)) {
            val (t, v) = synth(bpm, 30.0, 0.5)
            val r = PulseAnalyzer.analyze(t, v)
            assertTrue("$bpm → ${r.bpm} ${r.quality}", r.bpm != null && kotlin.math.abs(r.bpm - bpm) <= 3.0)
            assertEquals(Quality.GOOD, r.quality)
        }
    }

    @Test fun noiseGivesNoResult() {
        val r = Random(3)
        val t = DoubleArray(900) { it / 30.0 }; val v = DoubleArray(900) { 150 + r.nextDouble(-3.0, 3.0) }
        val res = PulseAnalyzer.analyze(t, v)
        assertEquals(Quality.UNRELIABLE, res.quality); assertNull(res.bpm)
    }

    @Test fun tooShort() = assertNull(PulseAnalyzer.analyze(doubleArrayOf(0.0, 5.0), doubleArrayOf(1.0, 2.0)).bpm)
}

class SleepTest {
    @Test fun snoreRhythm() {
        val ev = (0 until 10).map { SoundEventDetector.Event(it * 4.0, 1.2, 20.0) }
        assertTrue(SleepMath.isSnoreMinute(ev))
        val random = listOf(1.0, 3.0, 20.0, 21.0, 50.0).map { SoundEventDetector.Event(it, 1.0, 20.0) }
        assertTrue(!SleepMath.isSnoreMinute(random))
    }

    @Test fun eventDetector() {
        val d = SoundEventDetector()
        val events = ArrayList<SoundEventDetector.Event>()
        for (i in 0 until 600) {
            val db = if (i >= 300 && (i - 300) % 40 < 10) -40.0 else -70.0   // 1 s bursts every 4 s after 30 s
            d.add(db)?.let { events.add(it) }
        }
        assertTrue(events.size >= 6)
        assertEquals(1.0, events.first().durationSec, 0.15)
        assertTrue(SleepMath.isSnoreMinute(events))
    }

    @Test fun nightReport() {
        // 20 restless minutes, 7 h quiet with one 6-min awakening, 10 restless minutes at the end.
        val epochs = ArrayList<SleepEpoch>()
        repeat(20) { epochs.add(SleepEpoch(40, -60.0, -50.0, false, 0)) }
        repeat(220) { epochs.add(SleepEpoch(2, -65.0, -55.0, it % 4 == 0, 0)) }
        repeat(6) { epochs.add(SleepEpoch(50, -60.0, -50.0, false, 0)) }
        repeat(214) { epochs.add(SleepEpoch(1, -65.0, -55.0, false, 0)) }
        repeat(10) { epochs.add(SleepEpoch(60, -60.0, -50.0, false, 0)) }
        val r = SleepAnalysis.report(epochs, AgeGroup.ADULT)
        assertEquals(470, r.timeInBedMin); assertEquals(20, r.onsetLatencyMin); assertEquals(434, r.totalSleepMin)
        assertEquals(1, r.wakeBouts); assertEquals(55, r.snoreMin)
        assertTrue(SleepFlag.SHORT !in r.flags); assertTrue(SleepFlag.LOW_EFFICIENCY !in r.flags)
        assertTrue(SleepFlag.FREQUENT_SNORING in r.flags)
    }

    @Test fun shortNight() {
        val epochs = List(15) { SleepEpoch(40, -60.0, -50.0, false, 0) } + List(300) { SleepEpoch(1, -65.0, -55.0, false, 0) }
        val r = SleepAnalysis.report(epochs, AgeGroup.ADULT)
        assertTrue(SleepFlag.SHORT in r.flags)
    }
}
