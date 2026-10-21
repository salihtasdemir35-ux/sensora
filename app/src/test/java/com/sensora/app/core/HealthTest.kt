package com.sensora.app.core

import com.sensora.app.core.health.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabTest {
    @Test fun unitConversions() {
        val glu = Analyte.GLUCOSE_FASTING.units[1].toBase(5.5)        // mmol/L → mg/dL
        assertEquals(99.1, glu, 0.1)
        val a1c = Analyte.HBA1C.units[1].toBase(48.0)                 // 48 mmol/mol = 6.5 %
        assertEquals(6.54, a1c, 0.02)
        assertEquals(1.0, Analyte.CREATININE.units[1].toBase(88.42), 1e-9)
    }

    @Test fun glucoseAdaCategories() {
        assertNull(LabReference.interpret(Analyte.GLUCOSE_FASTING, 95.0).category)
        assertEquals(LabCategory.PREDIABETES_RANGE, LabReference.interpret(Analyte.GLUCOSE_FASTING, 100.0).category)
        assertEquals(LabCategory.DIABETES_RANGE, LabReference.interpret(Analyte.GLUCOSE_FASTING, 126.0).category)
        assertEquals(Urgency.URGENT, LabReference.interpret(Analyte.GLUCOSE_FASTING, 50.0).urgency)
        assertEquals(LabCategory.DIABETES_RANGE, LabReference.interpret(Analyte.HBA1C, 6.5).category)
        assertEquals(LabCategory.PREDIABETES_RANGE, LabReference.interpret(Analyte.HBA1C, 5.7).category)
    }

    @Test fun ldlNcepCategories() {
        assertEquals(LabCategory.OPTIMAL, LabReference.interpret(Analyte.LDL, 99.0).category)
        assertEquals(LabCategory.BORDERLINE_HIGH, LabReference.interpret(Analyte.LDL, 130.0).category)
        assertEquals(LabCategory.VERY_HIGH, LabReference.interpret(Analyte.LDL, 190.0).category)
    }

    @Test fun reportRangeHasPriority() {
        val r = LabReference.interpret(Analyte.ALT, 45.0, reportHigh = 50.0)
        assertTrue(r.usedReportRange); assertEquals(LabStatus.NORMAL, r.status)
        assertEquals(LabStatus.HIGH, LabReference.interpret(Analyte.ALT, 45.0).status)
    }

    @Test fun egfrCkdEpi2021() {
        // Reference calculator values (NKF CKD-EPI 2021): male 50 y, Scr 1.0 → 91; female 60 y, Scr 1.2 → 51.
        assertEquals(91.0, LabReference.egfrCkdEpi2021(1.0, 50, Sex.MALE), 1.0)
        assertEquals(51.0, LabReference.egfrCkdEpi2021(1.2, 60, Sex.FEMALE), 1.0)
        val r = LabReference.interpret(Analyte.CREATININE, 1.2, Sex.FEMALE, 60)
        assertEquals(EgfrStage.G3A, r.egfrStage)
    }

    @Test fun criticalPotassium() {
        assertEquals(Urgency.URGENT, LabReference.interpret(Analyte.POTASSIUM, 6.3).urgency)
        assertEquals(Urgency.SOON, LabReference.interpret(Analyte.POTASSIUM, 5.6).urgency)
        assertEquals(Urgency.NORMAL, LabReference.interpret(Analyte.POTASSIUM, 4.2).urgency)
    }
}

class VitalsTest {
    @Test fun esh2023() {
        assertEquals(BpCategory.OPTIMAL, BloodPressure.classify(118, 76).category)
        assertEquals(BpCategory.HIGH_NORMAL, BloodPressure.classify(132, 80).category)
        assertEquals(BpCategory.GRADE_1, BloodPressure.classify(128, 92).category)   // the higher category wins
        val isolated = BloodPressure.classify(150, 85)
        assertEquals(BpCategory.GRADE_1, isolated.category); assertTrue(isolated.isolatedSystolic)
        assertEquals(Urgency.URGENT, BloodPressure.classify(182, 100).urgency)
        assertEquals(BpCategory.LOW, BloodPressure.classify(85, 55).category)
        assertEquals(Urgency.SOON, BloodPressure.classify(120, 80, pulse = 135).pulseUrgency)
    }

    @Test fun oximetryAndPeakFlow() {
        assertEquals(Urgency.URGENT, Oximetry.urgency(92))
        assertEquals(Urgency.DISCUSS, Oximetry.urgency(94))
        assertEquals(Urgency.NORMAL, Oximetry.urgency(97))
        assertEquals(PefZone.GREEN, PeakFlow.zone(400.0, 500.0))
        assertEquals(PefZone.YELLOW, PeakFlow.zone(390.0, 500.0))
        assertEquals(PefZone.RED, PeakFlow.zone(240.0, 500.0))
    }
}

class SpirometryTest {
    @Test fun normalWithZScores() {
        val r = Spirometry.interpret(SpiroInput(fev1 = 3.5, fvc = 4.4, ratioZ = -0.5, fvcZ = 0.1, fev1Z = -0.2))
        assertEquals(SpiroPattern.NORMAL, r.pattern); assertEquals(SpiroMethod.Z_SCORE, r.method)
    }

    @Test fun obstructionSeverityAts2022() {
        val r = Spirometry.interpret(SpiroInput(fev1 = 1.6, fvc = 3.5, ratioZ = -3.0, fvcZ = -0.8, fev1Z = -3.1))
        assertEquals(SpiroPattern.OBSTRUCTION, r.pattern); assertEquals(SpiroSeverity.MODERATE, r.severity)
    }

    @Test fun fixedRatioAndGold() {
        val r = Spirometry.interpret(SpiroInput(fev1 = 1.4, fvc = 2.9, fev1Pct = 45.0, fvcPct = 85.0))
        assertEquals(SpiroMethod.FIXED_RATIO, r.method)
        assertEquals(SpiroPattern.OBSTRUCTION, r.pattern); assertEquals(3, r.goldGrade); assertEquals(Urgency.SOON, r.urgency)
    }

    @Test fun lowFvcAndRatioInPercent() {
        val r = Spirometry.interpret(SpiroInput(ratio = 84.0, fvcPct = 68.0))
        assertEquals(0.84, r.ratio!!, 1e-9); assertEquals(SpiroPattern.LOW_FVC, r.pattern)
    }

    @Test fun bronchodilator() {
        assertTrue(Spirometry.interpret(SpiroInput(fev1 = 2.0, fvc = 3.5, postFev1 = 2.3)).bronchodilatorSignificant!!)
        assertTrue(!Spirometry.interpret(SpiroInput(fev1 = 2.0, fvc = 3.5, postFev1 = 2.15)).bronchodilatorSignificant!!)
        // ATS/ERS 2022: change > 10 % of predicted
        assertTrue(Spirometry.interpret(SpiroInput(fev1 = 2.0, fvc = 3.5, postFev1 = 2.4, fev1Predicted = 3.5)).bronchodilatorSignificant!!)
    }
}

class HearingVisionTest {
    private fun ear(vararg v: Double?) = EarThresholds(v.toList())

    @Test fun who2021Grades() {
        assertEquals(HearingGrade.NORMAL, Audiogram.grade(19.9))
        assertEquals(HearingGrade.MILD, Audiogram.grade(20.0))
        assertEquals(HearingGrade.MODERATELY_SEVERE, Audiogram.grade(50.0))
        assertEquals(HearingGrade.COMPLETE, Audiogram.grade(95.0))
    }

    @Test fun asymmetryAndNotch() {
        val left = ear(10.0, 10.0, 15.0, 15.0, 45.0, 20.0)   // 4 kHz notch with recovery at 8 kHz
        val right = ear(10.0, 10.0, 10.0, 15.0, 20.0, 15.0)
        val r = Audiogram.interpret(left, right)
        assertEquals(21.25, r.leftPta!!, 1e-9)
        assertTrue(r.noiseNotchLeft); assertTrue(!r.noiseNotchRight)
        assertEquals(HearingGrade.NORMAL, r.overall)          // better ear decides
        val asym = Audiogram.interpret(ear(10.0, 10.0, 30.0, 40.0, 50.0, 60.0), ear(10.0, 10.0, 10.0, 15.0, 20.0, 25.0))
        assertTrue(asym.asymmetric); assertEquals(Urgency.DISCUSS, asym.urgency)
    }

    @Test fun icd11Vision() {
        assertEquals(VisionCategory.NO_IMPAIRMENT, Vision.category(Vision.fromSnellen(6.0, 12.0)))
        assertEquals(VisionCategory.MILD, Vision.category(Vision.fromSnellen(6.0, 18.0)))
        assertEquals(VisionCategory.MODERATE, Vision.category(Vision.fromSnellen(6.0, 24.0)))
        assertEquals(VisionCategory.SEVERE, Vision.category(Vision.fromSnellen(3.0, 60.0)))
        assertEquals(VisionCategory.BLINDNESS, Vision.category(Vision.fromSnellen(2.0, 60.0)))
        assertEquals(0.301, Vision.fromSnellen(20.0, 40.0).logMar, 1e-3)
        assertEquals("20/40", Vision.fromLogMar(0.3).snellen20)
    }

    @Test fun prescription() {
        val p = Prescription.interpret(-5.75, -1.0)
        assertEquals(-6.25, p.sphericalEquivalent, 1e-9); assertEquals(Refraction.HIGH_MYOPIA, p.refraction); assertTrue(p.astigmatism)
        assertEquals(Refraction.HYPEROPIA, Prescription.interpret(1.25).refraction)
    }
}

class BreathTestTest {
    private fun run(floor: Double, frames: List<Double>, frameSec: Double = 0.05, minDur: Double = 6.0): BlowTrial {
        val d = BlowDetector(frameSec, floor, minDur)
        for (db in frames) if (d.add(db, db > -1.0)) break
        repeat(20) { d.add(floor, false) }
        return d.result()
    }

    @Test fun goodBlow() {
        val f = List(10) { -70.0 } + List(4) { -40.0 + it * 5 } + List(140) { -25.0 - it * 0.05 } + List(20) { -70.0 }
        val r = run(-70.0, f)
        assertTrue(r.issues.toString(), r.acceptable)
        assertEquals(7.2, r.durationSec, 0.11)
        assertTrue(r.timeToPeakSec <= 0.5)
    }

    @Test fun shortInterruptedAndSlow() {
        val slow = List(5) { -70.0 } + List(30) { -50.0 + it * 0.5 } + List(20) { -70.0 }
        val rs = run(-70.0, slow)
        assertTrue(BlowIssue.SLOW_START in rs.issues); assertTrue(BlowIssue.TOO_SHORT in rs.issues)
        val cough = List(5) { -70.0 } + List(40) { -30.0 } + List(4) { -70.0 } + List(100) { -30.0 } + List(20) { -70.0 }
        assertTrue(BlowIssue.INTERRUPTED in run(-70.0, cough).issues)
        assertEquals(setOf(BlowIssue.NO_BLOW), run(-70.0, List(200) { -68.0 }).issues)
    }

    @Test fun repeatability() {
        val a = BlowTrial(7.0, 40.0, 0.2, emptySet()); val b = BlowTrial(6.6, 38.5, 0.2, emptySet()); val c = BlowTrial(6.2, 39.0, 0.3, emptySet())
        assertTrue(BreathSession.repeatable(listOf(a, b, c)))
        assertTrue(BreathSession.complete(listOf(a, b, c)))
        assertTrue(!BreathSession.repeatable(listOf(a, BlowTrial(5.0, 30.0, 0.2, emptySet()))))
    }
}
