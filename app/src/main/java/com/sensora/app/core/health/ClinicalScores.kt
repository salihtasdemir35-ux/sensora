package com.sensora.app.core.health

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

// ---------------- Blood pressure: ESH 2023 office categories ----------------
enum class BpCategory { LOW, OPTIMAL, NORMAL, HIGH_NORMAL, GRADE_1, GRADE_2, GRADE_3 }

data class BpResult(val category: BpCategory, val isolatedSystolic: Boolean, val urgency: Urgency, val pulseUrgency: Urgency?)

object BloodPressure {
    private fun sysCat(s: Int) = when { s < 120 -> BpCategory.OPTIMAL; s < 130 -> BpCategory.NORMAL; s < 140 -> BpCategory.HIGH_NORMAL; s < 160 -> BpCategory.GRADE_1; s < 180 -> BpCategory.GRADE_2; else -> BpCategory.GRADE_3 }
    private fun diaCat(d: Int) = when { d < 80 -> BpCategory.OPTIMAL; d < 85 -> BpCategory.NORMAL; d < 90 -> BpCategory.HIGH_NORMAL; d < 100 -> BpCategory.GRADE_1; d < 110 -> BpCategory.GRADE_2; else -> BpCategory.GRADE_3 }

    fun classify(systolic: Int, diastolic: Int, pulse: Int? = null): BpResult {
        val low = systolic < 90 || diastolic < 60
        val c = if (low && systolic < 120) BpCategory.LOW else maxOf(sysCat(systolic), diaCat(diastolic))
        val urgency = when (c) {
            BpCategory.GRADE_3 -> Urgency.URGENT
            BpCategory.GRADE_2 -> Urgency.SOON
            BpCategory.GRADE_1, BpCategory.HIGH_NORMAL, BpCategory.LOW -> Urgency.DISCUSS
            else -> Urgency.NORMAL
        }
        val pulseU = pulse?.let { p -> when { p < 40 || p > 130 -> Urgency.SOON; p < 50 || p > 100 -> Urgency.DISCUSS; else -> Urgency.NORMAL } }
        return BpResult(c, systolic >= 140 && diastolic < 90, urgency, pulseU)
    }
}

// ---------------- Pulse oximetry (BTS guidance) ----------------
object Oximetry {
    fun urgency(spo2: Int): Urgency = when { spo2 <= 92 -> Urgency.URGENT; spo2 <= 94 -> Urgency.DISCUSS; else -> Urgency.NORMAL }
}

// ---------------- Peak expiratory flow zones (NHLBI asthma action plan) ----------------
enum class PefZone { GREEN, YELLOW, RED }

object PeakFlow {
    fun zone(pef: Double, personalBest: Double): PefZone {
        val p = pef / personalBest
        return when { p >= 0.8 -> PefZone.GREEN; p >= 0.5 -> PefZone.YELLOW; else -> PefZone.RED }
    }
    fun urgency(z: PefZone) = when (z) { PefZone.GREEN -> Urgency.NORMAL; PefZone.YELLOW -> Urgency.SOON; PefZone.RED -> Urgency.URGENT }
}

// ---------------- Spirometry report: ATS/ERS 2022 interpretation, GOLD grades ----------------
enum class SpiroPattern { NORMAL, OBSTRUCTION, LOW_FVC, OBSTRUCTION_WITH_LOW_FVC, INSUFFICIENT_DATA }
enum class SpiroSeverity { MILD, MODERATE, SEVERE }
enum class SpiroMethod { Z_SCORE, LLN, FIXED_RATIO }

data class SpiroInput(
    val fev1: Double? = null, val fvc: Double? = null, val ratio: Double? = null,
    val fev1Pct: Double? = null, val fvcPct: Double? = null,
    val fev1Z: Double? = null, val fvcZ: Double? = null, val ratioZ: Double? = null,
    val ratioLln: Double? = null, val postFev1: Double? = null, val fev1Predicted: Double? = null,
)

data class SpiroResult(
    val pattern: SpiroPattern,
    val method: SpiroMethod?,
    val ratio: Double?,
    val severity: SpiroSeverity?,
    val goldGrade: Int?,
    val bronchodilatorSignificant: Boolean?,
    val urgency: Urgency,
)

object Spirometry {
    private const val LLN_Z = -1.645

    private fun asFraction(r: Double) = if (r > 1.5) r / 100.0 else r

    fun interpret(i: SpiroInput): SpiroResult {
        val ratio = i.ratio?.let { asFraction(it) } ?: if (i.fev1 != null && i.fvc != null && i.fvc > 0) i.fev1 / i.fvc else null
        val bd = if (i.postFev1 != null && i.fev1 != null) {
            val change = i.postFev1 - i.fev1
            if (i.fev1Predicted != null && i.fev1Predicted > 0) change / i.fev1Predicted > 0.10
            else change >= 0.200 && change / i.fev1 >= 0.12
        } else null
        if (ratio == null && i.ratioZ == null) return SpiroResult(SpiroPattern.INSUFFICIENT_DATA, null, null, null, null, bd, Urgency.NORMAL)

        val (obstruction, method) = when {
            i.ratioZ != null -> (i.ratioZ < LLN_Z) to SpiroMethod.Z_SCORE
            i.ratioLln != null && ratio != null -> (ratio < asFraction(i.ratioLln)) to SpiroMethod.LLN
            else -> (ratio!! < 0.70) to SpiroMethod.FIXED_RATIO
        }
        val lowFvc = when { i.fvcZ != null -> i.fvcZ < LLN_Z; i.fvcPct != null -> i.fvcPct < 80.0; else -> false }
        val pattern = when {
            obstruction && lowFvc -> SpiroPattern.OBSTRUCTION_WITH_LOW_FVC
            obstruction -> SpiroPattern.OBSTRUCTION
            lowFvc -> SpiroPattern.LOW_FVC
            else -> SpiroPattern.NORMAL
        }
        val abnormal = pattern != SpiroPattern.NORMAL
        val severity = if (abnormal && i.fev1Z != null) when {
            i.fev1Z >= -2.5 -> SpiroSeverity.MILD
            i.fev1Z >= -4.0 -> SpiroSeverity.MODERATE
            else -> SpiroSeverity.SEVERE
        } else null
        val gold = if (obstruction && i.fev1Pct != null) when {
            i.fev1Pct >= 80 -> 1; i.fev1Pct >= 50 -> 2; i.fev1Pct >= 30 -> 3; else -> 4
        } else null
        val urgency = when {
            !abnormal -> Urgency.NORMAL
            severity == SpiroSeverity.SEVERE || (gold ?: 0) >= 3 -> Urgency.SOON
            else -> Urgency.DISCUSS
        }
        return SpiroResult(pattern, method, ratio, severity, gold, bd, urgency)
    }
}

// ---------------- Audiogram: WHO 2021 grading ----------------
enum class HearingGrade { NORMAL, MILD, MODERATE, MODERATELY_SEVERE, SEVERE, PROFOUND, COMPLETE }

/** Thresholds in dB HL; index matches [Audiogram.FREQUENCIES]. */
data class EarThresholds(val values: List<Double?>)

data class AudiogramResult(
    val leftPta: Double?, val rightPta: Double?,
    val leftGrade: HearingGrade?, val rightGrade: HearingGrade?,
    val overall: HearingGrade?, val unilateral: Boolean,
    val asymmetric: Boolean, val noiseNotchLeft: Boolean, val noiseNotchRight: Boolean,
    val urgency: Urgency,
)

object Audiogram {
    val FREQUENCIES = listOf(250, 500, 1000, 2000, 4000, 8000)

    /** Four-frequency average of 0.5, 1, 2 and 4 kHz. */
    fun pta(e: EarThresholds): Double? {
        val v = listOf(1, 2, 3, 4).map { e.values.getOrNull(it) }
        return if (v.any { it == null }) null else v.filterNotNull().average()
    }

    fun grade(pta: Double): HearingGrade = when {
        pta < 20 -> HearingGrade.NORMAL
        pta < 35 -> HearingGrade.MILD
        pta < 50 -> HearingGrade.MODERATE
        pta < 65 -> HearingGrade.MODERATELY_SEVERE
        pta < 80 -> HearingGrade.SEVERE
        pta < 95 -> HearingGrade.PROFOUND
        else -> HearingGrade.COMPLETE
    }

    /** Coles-style notch: 4 kHz at least 10 dB worse than 2 kHz and than 8 kHz. */
    fun noiseNotch(e: EarThresholds): Boolean {
        val f2 = e.values.getOrNull(3); val f4 = e.values.getOrNull(4); val f8 = e.values.getOrNull(5)
        return f2 != null && f4 != null && f8 != null && f4 - f2 >= 10 && f4 - f8 >= 10
    }

    fun interpret(left: EarThresholds, right: EarThresholds): AudiogramResult {
        val pl = pta(left); val pr = pta(right)
        val gl = pl?.let { grade(it) }; val gr = pr?.let { grade(it) }
        val better = listOfNotNull(pl, pr).minOrNull()
        val worse = listOfNotNull(pl, pr).maxOrNull()
        val overall = better?.let { grade(it) }
        val unilateral = pl != null && pr != null && better!! < 20 && worse!! >= 35
        // ≥15 dB between ears at two adjacent frequencies, or ≥15 dB in the averages.
        val diffs = FREQUENCIES.indices.map { k ->
            val a = left.values.getOrNull(k); val b = right.values.getOrNull(k)
            if (a != null && b != null) abs(a - b) >= 15 else false
        }
        val adjacent = (0 until diffs.size - 1).any { diffs[it] && diffs[it + 1] }
        val asym = adjacent || (pl != null && pr != null && abs(pl - pr) >= 15)
        var u = when (overall) { null, HearingGrade.NORMAL -> Urgency.NORMAL; HearingGrade.MILD -> Urgency.DISCUSS; else -> Urgency.DISCUSS }
        if (asym || unilateral) u = Urgency.max(u, Urgency.DISCUSS)
        if (overall != null && overall.ordinal >= HearingGrade.SEVERE.ordinal) u = Urgency.max(u, Urgency.SOON)
        return AudiogramResult(pl, pr, gl, gr, overall, unilateral, asym, noiseNotch(left), noiseNotch(right), u)
    }
}

// ---------------- Vision: ICD-11 visual-impairment categories, refraction ----------------
enum class VisionCategory { NO_IMPAIRMENT, MILD, MODERATE, SEVERE, BLINDNESS }

data class Acuity(val decimal: Double) {
    val logMar: Double get() = -log10(decimal)
    /** Snellen denominator for a 6 m (or 20 ft) numerator. */
    val snellen6: String get() = "6/${(6 / decimal).roundToInt()}"
    val snellen20: String get() = "20/${(20 / decimal).roundToInt()}"
}

object Vision {
    fun fromSnellen(numerator: Double, denominator: Double) = Acuity(numerator / denominator)
    fun fromLogMar(logMar: Double) = Acuity(Math.pow(10.0, -logMar))

    /** Presenting distance acuity of the better eye; 6/18 itself counts as mild (ICD-11). */
    fun category(a: Acuity): VisionCategory = when {
        a.decimal >= 0.5 - 1e-9 -> VisionCategory.NO_IMPAIRMENT
        a.decimal >= 1.0 / 3.0 - 1e-6 -> VisionCategory.MILD
        a.decimal >= 0.1 - 1e-9 -> VisionCategory.MODERATE
        a.decimal >= 0.05 - 1e-9 -> VisionCategory.SEVERE
        else -> VisionCategory.BLINDNESS
    }

    fun urgency(c: VisionCategory) = when (c) {
        VisionCategory.NO_IMPAIRMENT -> Urgency.NORMAL
        VisionCategory.MILD, VisionCategory.MODERATE -> Urgency.DISCUSS
        else -> Urgency.SOON
    }
}

enum class Refraction { NONE, MYOPIA, HIGH_MYOPIA, HYPEROPIA, HIGH_HYPEROPIA }

data class PrescriptionResult(val sphericalEquivalent: Double, val refraction: Refraction, val astigmatism: Boolean, val presbyopiaAdd: Boolean, val urgency: Urgency)

object Prescription {
    /** IMI definitions: myopia SE ≤ −0.50 D, high myopia SE ≤ −6.00 D. */
    fun interpret(sph: Double, cyl: Double = 0.0, add: Double = 0.0): PrescriptionResult {
        val se = sph + cyl / 2.0
        val r = when {
            se <= -6.0 -> Refraction.HIGH_MYOPIA
            se <= -0.5 -> Refraction.MYOPIA
            se >= 5.0 -> Refraction.HIGH_HYPEROPIA
            se >= 0.5 -> Refraction.HYPEROPIA
            else -> Refraction.NONE
        }
        return PrescriptionResult(se, r, abs(cyl) >= 0.5, add > 0.0, if (r == Refraction.HIGH_MYOPIA) Urgency.DISCUSS else Urgency.NORMAL)
    }
}
