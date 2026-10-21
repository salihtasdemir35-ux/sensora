package com.sensora.app.core.health

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A unit the user may type, with a conversion into the analyte's base unit. */
class LabUnit(val symbol: String, val toBase: (Double) -> Double)

private fun linear(symbol: String, factor: Double) = LabUnit(symbol) { it * factor }

enum class Analyte(val units: List<LabUnit>) {
    HEMOGLOBIN(listOf(linear("g/dL", 1.0), linear("g/L", 0.1))),
    WBC(listOf(linear("×10³/µL", 1.0), linear("×10⁹/L", 1.0))),
    PLATELETS(listOf(linear("×10³/µL", 1.0), linear("×10⁹/L", 1.0))),
    GLUCOSE_FASTING(listOf(linear("mg/dL", 1.0), linear("mmol/L", 18.016))),
    HBA1C(listOf(linear("%", 1.0), LabUnit("mmol/mol") { it / 10.929 + 2.15 })),
    TOTAL_CHOLESTEROL(listOf(linear("mg/dL", 1.0), linear("mmol/L", 38.67))),
    LDL(listOf(linear("mg/dL", 1.0), linear("mmol/L", 38.67))),
    HDL(listOf(linear("mg/dL", 1.0), linear("mmol/L", 38.67))),
    TRIGLYCERIDES(listOf(linear("mg/dL", 1.0), linear("mmol/L", 88.57))),
    CREATININE(listOf(linear("mg/dL", 1.0), linear("µmol/L", 1.0 / 88.42))),
    ALT(listOf(linear("U/L", 1.0))),
    AST(listOf(linear("U/L", 1.0))),
    TSH(listOf(linear("mIU/L", 1.0), linear("µIU/mL", 1.0))),
    VITAMIN_D(listOf(linear("ng/mL", 1.0), linear("nmol/L", 1.0 / 2.496))),
    B12(listOf(linear("pg/mL", 1.0), linear("pmol/L", 1.355))),
    FERRITIN(listOf(linear("ng/mL", 1.0), linear("µg/L", 1.0))),
    SODIUM(listOf(linear("mmol/L", 1.0), linear("mEq/L", 1.0))),
    POTASSIUM(listOf(linear("mmol/L", 1.0), linear("mEq/L", 1.0))),
    CRP(listOf(linear("mg/L", 1.0), linear("mg/dL", 10.0)));

    val baseUnit: String get() = units.first().symbol
}

enum class LabStatus { LOW, NORMAL, HIGH }

/** Guideline categories that add meaning beyond low/normal/high. */
enum class LabCategory {
    HYPOGLYCEMIA, PREDIABETES_RANGE, DIABETES_RANGE,
    DESIRABLE, OPTIMAL, NEAR_OPTIMAL, BORDERLINE_HIGH, HIGH, VERY_HIGH,
    LOW_HDL, PROTECTIVE_HDL,
    DEFICIENT, INSUFFICIENT, SUFFICIENT, POSSIBLY_EXCESSIVE,
    IRON_DEFICIENCY_LIKELY, BORDERLINE_LOW,
}

enum class EgfrStage { G1, G2, G3A, G3B, G4, G5 }

data class LabResult(
    val analyte: Analyte,
    val value: Double,
    val low: Double?,
    val high: Double?,
    val usedReportRange: Boolean,
    val status: LabStatus,
    val category: LabCategory?,
    val urgency: Urgency,
    val egfr: Double? = null,
    val egfrStage: EgfrStage? = null,
)

/**
 * Typical adult reference values and guideline categories. Laboratory ranges differ, so the range
 * printed on the user's own report always takes priority. Critical thresholds follow common
 * laboratory "panic value" practice and are deliberately conservative.
 */
object LabReference {
    private fun range(a: Analyte, sex: Sex?): Pair<Double?, Double?> = when (a) {
        Analyte.HEMOGLOBIN -> if (sex == Sex.MALE) 13.5 to 17.5 else 12.0 to 15.5
        Analyte.WBC -> 4.0 to 11.0
        Analyte.PLATELETS -> 150.0 to 450.0
        Analyte.GLUCOSE_FASTING -> 70.0 to 99.0
        Analyte.HBA1C -> null to 5.6
        Analyte.TOTAL_CHOLESTEROL -> null to 199.0
        Analyte.LDL -> null to 129.0
        Analyte.HDL -> (if (sex == Sex.MALE) 40.0 else 50.0) to null
        Analyte.TRIGLYCERIDES -> null to 149.0
        Analyte.CREATININE -> if (sex == Sex.MALE) 0.7 to 1.3 else 0.5 to 1.1
        Analyte.ALT -> null to 40.0
        Analyte.AST -> null to 40.0
        Analyte.TSH -> 0.4 to 4.0
        Analyte.VITAMIN_D -> 30.0 to 100.0
        Analyte.B12 -> 200.0 to 900.0
        Analyte.FERRITIN -> if (sex == Sex.MALE) 30.0 to 400.0 else 15.0 to 150.0
        Analyte.SODIUM -> 135.0 to 145.0
        Analyte.POTASSIUM -> 3.5 to 5.0
        Analyte.CRP -> null to 5.0
    }

    /** CKD-EPI 2021 (race-free) estimated GFR, mL/min/1.73 m². */
    fun egfrCkdEpi2021(creatinineMgDl: Double, age: Int, sex: Sex): Double {
        val k = if (sex == Sex.FEMALE) 0.7 else 0.9
        val a = if (sex == Sex.FEMALE) -0.241 else -0.302
        val r = creatinineMgDl / k
        return 142.0 * min(r, 1.0).pow(a) * max(r, 1.0).pow(-1.200) * 0.9938.pow(age) * (if (sex == Sex.FEMALE) 1.012 else 1.0)
    }

    fun egfrStage(egfr: Double): EgfrStage = when {
        egfr >= 90 -> EgfrStage.G1
        egfr >= 60 -> EgfrStage.G2
        egfr >= 45 -> EgfrStage.G3A
        egfr >= 30 -> EgfrStage.G3B
        egfr >= 15 -> EgfrStage.G4
        else -> EgfrStage.G5
    }

    /**
     * @param value value already converted to the analyte's base unit.
     * @param reportLow/reportHigh the range printed on the report, in base units (optional).
     */
    fun interpret(a: Analyte, value: Double, sex: Sex? = null, age: Int? = null, reportLow: Double? = null, reportHigh: Double? = null): LabResult {
        val usedReport = reportLow != null || reportHigh != null
        val (dl, dh) = range(a, sex)
        val low = if (usedReport) reportLow else dl
        val high = if (usedReport) reportHigh else dh
        val status = when {
            low != null && value < low -> LabStatus.LOW
            high != null && value > high -> LabStatus.HIGH
            else -> LabStatus.NORMAL
        }
        var urgency = if (status == LabStatus.NORMAL) Urgency.NORMAL else Urgency.DISCUSS
        var category: LabCategory? = null
        var egfr: Double? = null
        var stage: EgfrStage? = null
        fun at(u: Urgency) { urgency = Urgency.max(urgency, u) }

        when (a) {
            Analyte.HEMOGLOBIN -> { if (value < 8.0) at(Urgency.SOON); if (value < 7.0 || value > 20.0) at(Urgency.URGENT) }
            Analyte.WBC -> { if (value < 2.0 || value > 30.0) at(Urgency.URGENT) else if (value > 20.0) at(Urgency.SOON) }
            Analyte.PLATELETS -> { if (value < 20.0) at(Urgency.URGENT) else if (value < 50.0 || value > 1000.0) at(Urgency.SOON) }
            Analyte.GLUCOSE_FASTING -> {
                category = when { value < 70 -> LabCategory.HYPOGLYCEMIA; value < 100 -> null; value < 126 -> LabCategory.PREDIABETES_RANGE; else -> LabCategory.DIABETES_RANGE }
                if (category != null) at(Urgency.DISCUSS)
                if (value >= 250) at(Urgency.SOON)
                if (value < 54 || value >= 400) at(Urgency.URGENT)
            }
            Analyte.HBA1C -> {
                category = when { value < 5.7 -> null; value < 6.5 -> LabCategory.PREDIABETES_RANGE; else -> LabCategory.DIABETES_RANGE }
                if (category != null) at(Urgency.DISCUSS)
                if (value >= 10.0) at(Urgency.SOON)
            }
            Analyte.TOTAL_CHOLESTEROL -> category = when { value < 200 -> LabCategory.DESIRABLE; value < 240 -> LabCategory.BORDERLINE_HIGH; else -> LabCategory.HIGH }
            Analyte.LDL -> {
                category = when { value < 100 -> LabCategory.OPTIMAL; value < 130 -> LabCategory.NEAR_OPTIMAL; value < 160 -> LabCategory.BORDERLINE_HIGH; value < 190 -> LabCategory.HIGH; else -> LabCategory.VERY_HIGH }
                if (value >= 190) at(Urgency.SOON)
            }
            Analyte.HDL -> category = when { value < (if (sex == Sex.MALE) 40.0 else 50.0) -> LabCategory.LOW_HDL; value >= 60 -> LabCategory.PROTECTIVE_HDL; else -> null }
            Analyte.TRIGLYCERIDES -> {
                category = when { value < 150 -> null; value < 200 -> LabCategory.BORDERLINE_HIGH; value < 500 -> LabCategory.HIGH; else -> LabCategory.VERY_HIGH }
                if (value >= 500) at(Urgency.SOON)
            }
            Analyte.CREATININE -> if (age != null && sex != null && age >= 18) {
                egfr = egfrCkdEpi2021(value, age, sex)
                stage = egfrStage(egfr)
                when (stage) {
                    EgfrStage.G3A, EgfrStage.G3B -> at(Urgency.DISCUSS)
                    EgfrStage.G4, EgfrStage.G5 -> at(Urgency.SOON)
                    else -> Unit
                }
            }
            Analyte.ALT, Analyte.AST -> { if (value > 200) at(Urgency.SOON); if (value > 1000) at(Urgency.URGENT) }
            Analyte.TSH -> if (value < 0.1 || value > 10.0) at(Urgency.SOON)
            Analyte.VITAMIN_D -> {
                category = when { value < 20 -> LabCategory.DEFICIENT; value < 30 -> LabCategory.INSUFFICIENT; value <= 100 -> LabCategory.SUFFICIENT; else -> LabCategory.POSSIBLY_EXCESSIVE }
                if (value > 150) at(Urgency.SOON)
            }
            Analyte.B12 -> if (value in 200.0..300.0) { category = LabCategory.BORDERLINE_LOW; at(Urgency.DISCUSS) }
            Analyte.FERRITIN -> { if (value < 30) { category = LabCategory.IRON_DEFICIENCY_LIKELY; at(Urgency.DISCUSS) }; if (value > 1000) at(Urgency.SOON) }
            Analyte.SODIUM -> { if (value < 130 || value > 150) at(Urgency.SOON); if (value < 125 || value > 155) at(Urgency.URGENT) }
            Analyte.POTASSIUM -> { if (value < 3.0 || value > 6.0) at(Urgency.URGENT) else if (value >= 5.5) at(Urgency.SOON) }
            Analyte.CRP -> { if (value > 100) at(Urgency.SOON) }
        }
        return LabResult(a, value, low, high, usedReport, status, category, urgency, egfr, stage)
    }
}
