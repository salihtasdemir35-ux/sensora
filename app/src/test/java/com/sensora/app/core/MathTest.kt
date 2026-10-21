package com.sensora.app.core

import com.sensora.app.core.math.AtmosphereMath
import com.sensora.app.core.math.AudioMath
import com.sensora.app.core.math.GeoMath
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.math.RollingWindow
import com.sensora.app.core.math.Series
import com.sensora.app.core.math.Stats
import com.sensora.app.core.math.TrackAccumulator
import com.sensora.app.core.math.TrackPoint
import com.sensora.app.core.math.UnitCategory
import com.sensora.app.core.math.UnitConverter
import com.sensora.app.core.model.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class OrientationMathTest {
    @Test fun normalize() {
        assertEquals(350.0, OrientationMath.normalizeDegrees(-10.0), 1e-9)
        assertEquals(10.0, OrientationMath.normalizeDegrees(370.0), 1e-9)
    }
    @Test fun signedDifferenceWraps() {
        assertEquals(20.0, OrientationMath.signedDifference(10.0, 350.0), 1e-9)
        assertEquals(-20.0, OrientationMath.signedDifference(350.0, 10.0), 1e-9)
    }
    @Test fun smoothingAcrossNorth() {
        assertEquals(0.0, OrientationMath.smoothAngle(350.0, 10.0, 0.5), 1e-9)
    }
    @Test fun cardinal() {
        assertEquals(0, OrientationMath.cardinal16(0.0))
        assertEquals(0, OrientationMath.cardinal16(11.0))
        assertEquals(1, OrientationMath.cardinal16(12.0))
        assertEquals(4, OrientationMath.cardinal16(90.0))
        assertEquals(0, OrientationMath.cardinal16(359.0))
    }
    @Test fun tiltFlatAndSideways() {
        val flat = OrientationMath.tilt(0.0, 0.0, 9.81)
        assertEquals(0.0, flat.rollDeg, 1e-9); assertEquals(0.0, flat.pitchDeg, 1e-9)
        assertEquals(90.0, OrientationMath.tilt(9.81, 0.0, 0.0).rollDeg, 1e-9)
        assertEquals(0.0, OrientationMath.totalTilt(0.0, 0.0, 9.81), 1e-9)
    }
    @Test fun verticalDeviation() {
        assertEquals(0.0, OrientationMath.verticalDeviation(0.0, 9.81), 1e-9)
        assertEquals(5.82, OrientationMath.verticalDeviation(1.0, 9.81), 0.01)
        assertEquals(-2.92, OrientationMath.verticalDeviation(9.81, 0.5), 0.01)
    }
    @Test fun slope() {
        assertEquals(100.0, OrientationMath.slopePercent(45.0), 1e-9)
        assertEquals(1000.0, OrientationMath.slopeMmPerM(45.0), 1e-9)
    }
}

class AtmosphereMathTest {
    @Test fun seaLevelIsZero() = assertEquals(0.0, AtmosphereMath.altitudeFromPressure(1013.25), 1e-9)
    @Test fun standardAt1000m() = assertEquals(1000.0, AtmosphereMath.altitudeFromPressure(898.76), 2.0)
    @Test fun calibrationRoundTrip() {
        val p0 = AtmosphereMath.seaLevelPressure(950.0, 500.0)
        assertEquals(500.0, AtmosphereMath.altitudeFromPressure(950.0, p0), 1e-6)
    }
}

class AudioMathTest {
    @Test fun fullScaleSineIsMinus3dBFS() {
        val n = 44100
        val buf = ShortArray(n) { (32767 * sin(2 * PI * 1000 * it / 44100.0)).toInt().toShort() }
        assertEquals(-3.01, AudioMath.dbfs(AudioMath.rms(buf, n)), 0.05)
    }
    @Test fun silenceIsFloor() = assertEquals(AudioMath.MIN_DB, AudioMath.dbfs(AudioMath.rms(ShortArray(100), 100)), 0.0)
    @Test fun energyAverage() {
        assertEquals(60.0, AudioMath.energyAverage(listOf(60.0, 60.0)), 1e-9)
        assertEquals(67.40, AudioMath.energyAverage(listOf(60.0, 70.0)), 0.01)
    }
    @Test fun clipping() {
        val buf = ShortArray(100) { if (it < 5) 32767 else 0 }
        assertEquals(0.05, AudioMath.clippingRatio(buf, 100), 1e-9)
    }
}

class UnitConverterTest {
    @Test fun knownValues() {
        assertEquals(1.609344, UnitConverter.convert(1.0, UnitCategory.LENGTH, "mi", "km"), 1e-9)
        assertEquals(212.0, UnitConverter.convert(100.0, UnitCategory.TEMPERATURE, "C", "F"), 1e-9)
        assertEquals(-273.15, UnitConverter.convert(0.0, UnitCategory.TEMPERATURE, "K", "C"), 1e-9)
        assertEquals(14.5038, UnitConverter.convert(1.0, UnitCategory.PRESSURE, "bar", "psi"), 1e-4)
        assertEquals(3.6e6, UnitConverter.convert(1.0, UnitCategory.ENERGY, "kwh", "j"), 1e-6)
        assertEquals(100.0, UnitConverter.convert(27.7777777778, UnitCategory.SPEED, "mps", "kmh"), 1e-6)
    }
    @Test fun roundTripEveryUnit() {
        UnitCategory.entries.forEach { c ->
            val units = UnitConverter.units(c)
            units.forEach { a -> units.forEach { b ->
                val there = UnitConverter.convert(123.456, c, a.id, b.id)
                assertEquals("$c ${a.id}->${b.id}", 123.456, UnitConverter.convert(there, c, b.id, a.id), 1e-6)
            } }
        }
    }
}

class StatisticsTest {
    @Test fun stats() {
        val s = listOf(3.0, 1.0, 2.0, Double.NaN).fold(Stats.EMPTY) { acc, v -> acc.add(v) }
        assertEquals(3, s.count); assertEquals(1.0, s.min, 0.0); assertEquals(3.0, s.max, 0.0); assertEquals(2.0, s.mean, 1e-9)
    }
    @Test fun rollingStdDev() {
        val w = RollingWindow(3)
        listOf(100.0, 1.0, 2.0, 3.0).forEach { w.add(it) }
        assertEquals(3, w.size); assertEquals(1.0, w.stdDev(), 1e-9)
    }
    @Test fun downsample() {
        val out = Series.downsample(List(10) { it.toFloat() }, 5)
        assertEquals(listOf(0.5f, 2.5f, 4.5f, 6.5f, 8.5f), out)
        assertEquals(3, Series.downsample(listOf(1f, 2f, 3f), 10).size)
    }
}

class GeoTest {
    @Test fun oneDegreeLatitude() = assertEquals(111_195.0, GeoMath.haversine(0.0, 0.0, 1.0, 0.0), 1.0)

    @Test fun trackIgnoresJitterAndBadFixes() {
        val t = TrackAccumulator()
        for (i in 0 until 10) t.add(TrackPoint(41.0 + (if (i % 2 == 0) 0.000005 else 0.0), 29.0, 5f, i * 1000L))
        assertEquals(0.0, t.distanceM, 1e-9)
        t.add(TrackPoint(41.1, 29.0, 500f, 20_000L))
        assertEquals(0.0, t.distanceM, 1e-9)
    }

    @Test fun trackAccumulatesMovement() {
        val t = TrackAccumulator()
        val step = 10.0 / 111_195.0
        for (i in 0..10) t.add(TrackPoint(41.0 + i * step, 29.0, 4f, i * 1000L, speedMps = 10f))
        assertEquals(100.0, t.distanceM, 0.5)
        assertEquals(10.0, t.maxSpeedMps, 1e-6)
        assertEquals(10.0, t.averageSpeedMps(10_000)!!, 0.1)
    }
}

class QualityRulesTest {
    @Test fun compass() {
        assertEquals(Quality.GOOD, QualityRules.compass(3, 45.0))
        assertEquals(Quality.LIMITED, QualityRules.compass(3, 90.0))
        assertEquals(Quality.UNRELIABLE, QualityRules.compass(0, 45.0))
        assertTrue(QualityRules.fieldInterference(150.0))
    }
    @Test fun gps() {
        assertEquals(Quality.GOOD, QualityRules.gps(5f, 1000))
        assertEquals(Quality.LIMITED, QualityRules.gps(20f, 1000))
        assertEquals(Quality.UNRELIABLE, QualityRules.gps(5f, 60_000))
        assertEquals(Quality.UNRELIABLE, QualityRules.gps(null, 0))
    }
    @Test fun uncalibratedMicNeverGood() {
        assertEquals(Quality.LIMITED, QualityRules.soundMeter(calibrated = false, clipping = false))
        assertEquals(Quality.UNRELIABLE, QualityRules.soundMeter(calibrated = true, clipping = true))
    }
    @Test fun saturatedLight() = assertEquals(Quality.LIMITED, QualityRules.light(9900.0, 10000.0))
    @Test fun stability() {
        assertEquals(Quality.UNRELIABLE, QualityRules.stability(Double.NaN))
        assertEquals(Quality.GOOD, QualityRules.stability(0.05))
    }
}
