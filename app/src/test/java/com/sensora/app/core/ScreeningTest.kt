package com.sensora.app.core

import com.sensora.app.core.hearing.Ear
import com.sensora.app.core.hearing.EarResult
import com.sensora.app.core.hearing.HearingScreening
import com.sensora.app.core.hearing.ThresholdSearch
import com.sensora.app.core.vision.ColorAxis
import com.sensora.app.core.vision.ColorScience
import com.sensora.app.core.vision.ColorScoring
import com.sensora.app.core.vision.ContrastScorer
import com.sensora.app.core.vision.EtdrsScorer
import com.sensora.app.core.vision.Optotype
import com.sensora.app.core.vision.PlateGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class HughsonWestlakeTest {
    private fun run(trueThreshold: Int, start: Int = -40): ThresholdSearch {
        val s = ThresholdSearch(start)
        while (!s.done) s.respond(s.level >= trueThreshold)
        return s
    }

    @Test fun convergesToThreshold() {
        for (t in listOf(-80, -63, -45, -20, -12)) {
            val s = run(t)
            val got = s.threshold!!
            assertTrue("true $t got $got", got >= t && got - t < 5)
            assertTrue(s.presentations < 30)
        }
    }

    @Test fun noResponse() = assertNull(run(10).threshold)

    @Test fun veryGoodHearingHitsFloor() = assertEquals(-95, run(-120).threshold)

    @Test fun asymmetryCriteria() {
        val f = HearingScreening.ORDERED
        val right = f.map { EarResult(Ear.RIGHT, it, -60) }
        val leftSym = f.map { EarResult(Ear.LEFT, it, -55) }
        assertTrue(!HearingScreening.evaluate(right + leftSym, -60 to -55, 6, 0).asymmetric)
        val leftAsym = f.map { EarResult(Ear.LEFT, it, if (it >= 2000) -42 else -58) }   // 18 dB at 2k, 4k, 8k
        val r = HearingScreening.evaluate(right + leftAsym, -60 to -60, 6, 0)
        assertTrue(r.asymmetric); assertEquals(listOf(2000, 4000, 8000), r.asymmetricFrequencies)
        val single = f.map { EarResult(Ear.LEFT, it, if (it == 4000) -35 else -60) }   // 25 dB at one frequency
        assertEquals(listOf(4000), HearingScreening.evaluate(right + single, null to null, 6, 0).asymmetricFrequencies)
    }

    @Test fun reliability() {
        val f = HearingScreening.ORDERED.flatMap { listOf(EarResult(Ear.RIGHT, it, -60), EarResult(Ear.LEFT, it, -60)) }
        assertTrue(!HearingScreening.evaluate(f, -60 to -40, 6, 0).reliable)   // retest differs by 20 dB
        assertTrue(!HearingScreening.evaluate(f, -60 to -55, 8, 3).reliable)   // 37 % false alarms
        assertTrue(HearingScreening.evaluate(f, -60 to -55, 8, 1).reliable)
    }
}

class VisionScreeningTest {
    @Test fun optotypeSize() {
        // logMAR 0 at 6 m = 8.73 mm; at 40 cm = 0.58 mm; logMAR 1.0 at 3 m = 43.6 mm
        assertEquals(8.73, Optotype.letterHeightMm(0.0, 6000.0), 0.01)
        assertEquals(0.582, Optotype.letterHeightMm(0.0, 400.0), 0.002)
        assertEquals(43.6, Optotype.letterHeightMm(1.0, 3000.0), 0.1)
    }

    @Test fun etdrsScoring() {
        // Reads every optotype down to 0.1 fully, 2 of 5 on 0.0 → 0.0 + 0.1 − 0.02 × (35 + 2) = 0.06? (start 0.7)
        val s = EtdrsScorer(7)
        repeat(7) { repeat(5) { s.record(true) } }        // lines 0.7 … 0.1
        repeat(5) { i -> s.record(i < 2) }                // line 0.0: 2 correct
        assertTrue(s.done)
        assertEquals(0.8 - 0.02 * 37, s.score, 1e-9)       // = 0.06
    }

    @Test fun etdrsRestartsLarger() {
        val s = EtdrsScorer(7)
        repeat(5) { s.record(false) }                     // fails 0.7 → restarts at 1.0
        assertEquals(10, s.line)
        repeat(5) { s.record(true) }; repeat(5) { s.record(true) }; repeat(5) { s.record(false) }   // 1.0, 0.9 ok; 0.8 fail
        assertEquals(1.1 - 0.02 * 10, s.score, 1e-9)      // = 0.9
    }

    @Test fun contrastScoring() {
        val c = ContrastScorer()
        repeat(11) { repeat(3) { c.record(true) } }       // levels 0 … 10 (logCS 1.50)
        c.record(true); c.record(false); c.record(false)  // level 11 failed
        assertTrue(c.done); assertEquals(1.50, c.logCs, 1e-9)
        assertEquals(1, ContrastScorer.interpretation(1.5))
    }

    @Test fun greyLevels() {
        assertEquals(255, ColorScience.greyForContrast(0.0))
        val g = ColorScience.greyForContrast(0.5)
        assertEquals(0.5, ColorScience.achievedContrast(g), 0.01)
    }

    @Test fun colourRoundTrip() {
        val xyz = ColorScience.rgbToXyz(0.2, 0.6, 0.4)
        val rgb = ColorScience.xyzToRgb(xyz[0], xyz[1], xyz[2])!!
        assertEquals(0.2, rgb[0], 1e-3); assertEquals(0.6, rgb[1], 1e-3); assertEquals(0.4, rgb[2], 1e-3)
    }

    @Test fun confusionPairIsOnLineThroughCopunctalPoint() {
        for (axis in listOf(ColorAxis.PROTAN, ColorAxis.DEUTAN, ColorAxis.TRITAN)) {
            val (f, b) = PlateGenerator.confusionPair(axis, 0.36, 0.40, 0.04)
            val (px, py) = PlateGenerator.COPUNCTAL.getValue(axis)
            val cross = (f.first - px) * (b.second - py) - (f.second - py) * (b.first - px)
            assertTrue("$axis $cross", abs(cross) < 1e-9)
        }
    }

    @Test fun platesAreDrawableAndScored() {
        val plates = PlateGenerator.series(7)
        assertEquals(10, plates.size)
        assertTrue(plates.all { it.dots.size > 250 })
        val perfect = ColorScoring.score(plates, plates.map { it.digit })
        assertTrue(perfect.valid && !perfect.redGreenSuspected && !perfect.tritanSuspected)
        val rgMiss = ColorScoring.score(plates, plates.map { if (it.axis == ColorAxis.PROTAN || it.axis == ColorAxis.DEUTAN) null else it.digit })
        assertTrue(rgMiss.redGreenSuspected && !rgMiss.tritanSuspected)
        val controlMiss = ColorScoring.score(plates, plates.map { if (it.axis == ColorAxis.CONTROL) 0 else it.digit })
        assertTrue(!controlMiss.valid && !controlMiss.redGreenSuspected)
    }

    @Test fun plateFigureDotsDifferInHueNotLuminance() {
        val p = PlateGenerator.plate(ColorAxis.DEUTAN, 8, 3)
        val lum = p.dots.map { d -> ColorScience.rgbToXyz(((d.rgb shr 16) and 255) / 255.0, ((d.rgb shr 8) and 255) / 255.0, (d.rgb and 255) / 255.0)[1] }
        assertTrue(lum.minOrNull()!! > 0.25 && lum.maxOrNull()!! < 0.6)
        assertTrue(p.dots.map { it.rgb }.toSet().size > 50)   // luminance noise per dot
    }
}
