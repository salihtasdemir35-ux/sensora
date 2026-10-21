package com.sensora.app.ui.health

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.health.*
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*

@Composable
fun ResultsInterpreterScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        var panel by rememberSaveable { mutableStateOf(0) }
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.interp_disclaimer))
            ChoiceChips(listOf(stringResource(R.string.panel_lab), stringResource(R.string.panel_bp), stringResource(R.string.panel_spo2),
                stringResource(R.string.panel_lung), stringResource(R.string.panel_hearing), stringResource(R.string.panel_vision)), panel) { panel = it }
            when (panel) {
                0 -> LabPanel(tool)
                1 -> BpPanel(tool)
                2 -> Spo2Panel(tool)
                3 -> LungPanel(tool)
                4 -> HearingPanel(tool)
                else -> VisionPanel(tool)
            }
            Note(R.string.interp_emergency)
        }
    }
}

// ------------------------------------------------------------ Laboratory
@StringRes private fun analyteName(a: Analyte): Int = when (a) {
    Analyte.HEMOGLOBIN -> R.string.lab_hb; Analyte.WBC -> R.string.lab_wbc; Analyte.PLATELETS -> R.string.lab_plt
    Analyte.GLUCOSE_FASTING -> R.string.lab_glucose; Analyte.HBA1C -> R.string.lab_hba1c; Analyte.TOTAL_CHOLESTEROL -> R.string.lab_tc
    Analyte.LDL -> R.string.lab_ldl; Analyte.HDL -> R.string.lab_hdl; Analyte.TRIGLYCERIDES -> R.string.lab_tg
    Analyte.CREATININE -> R.string.lab_creatinine; Analyte.ALT -> R.string.lab_alt; Analyte.AST -> R.string.lab_ast
    Analyte.TSH -> R.string.lab_tsh; Analyte.VITAMIN_D -> R.string.lab_vitd; Analyte.B12 -> R.string.lab_b12
    Analyte.FERRITIN -> R.string.lab_ferritin; Analyte.SODIUM -> R.string.lab_na; Analyte.POTASSIUM -> R.string.lab_k; Analyte.CRP -> R.string.lab_crp
}

@StringRes private fun analyteInfo(a: Analyte): Int = when (a) {
    Analyte.HEMOGLOBIN -> R.string.info_hb; Analyte.WBC -> R.string.info_wbc; Analyte.PLATELETS -> R.string.info_plt
    Analyte.GLUCOSE_FASTING -> R.string.info_glucose; Analyte.HBA1C -> R.string.info_hba1c; Analyte.TOTAL_CHOLESTEROL -> R.string.info_tc
    Analyte.LDL -> R.string.info_ldl; Analyte.HDL -> R.string.info_hdl; Analyte.TRIGLYCERIDES -> R.string.info_tg
    Analyte.CREATININE -> R.string.info_creatinine; Analyte.ALT -> R.string.info_alt; Analyte.AST -> R.string.info_ast
    Analyte.TSH -> R.string.info_tsh; Analyte.VITAMIN_D -> R.string.info_vitd; Analyte.B12 -> R.string.info_b12
    Analyte.FERRITIN -> R.string.info_ferritin; Analyte.SODIUM -> R.string.info_na; Analyte.POTASSIUM -> R.string.info_k; Analyte.CRP -> R.string.info_crp
}

@StringRes private fun categoryName(c: LabCategory): Int = when (c) {
    LabCategory.HYPOGLYCEMIA -> R.string.cat_hypoglycemia; LabCategory.PREDIABETES_RANGE -> R.string.cat_prediabetes
    LabCategory.DIABETES_RANGE -> R.string.cat_diabetes; LabCategory.DESIRABLE -> R.string.cat_desirable
    LabCategory.OPTIMAL -> R.string.cat_optimal; LabCategory.NEAR_OPTIMAL -> R.string.cat_near_optimal
    LabCategory.BORDERLINE_HIGH -> R.string.cat_borderline_high; LabCategory.HIGH -> R.string.cat_high
    LabCategory.VERY_HIGH -> R.string.cat_very_high; LabCategory.LOW_HDL -> R.string.cat_low_hdl
    LabCategory.PROTECTIVE_HDL -> R.string.cat_protective_hdl; LabCategory.DEFICIENT -> R.string.cat_deficient
    LabCategory.INSUFFICIENT -> R.string.cat_insufficient; LabCategory.SUFFICIENT -> R.string.cat_sufficient
    LabCategory.POSSIBLY_EXCESSIVE -> R.string.cat_excessive; LabCategory.IRON_DEFICIENCY_LIKELY -> R.string.cat_iron_def
    LabCategory.BORDERLINE_LOW -> R.string.cat_borderline_low
}

private data class LabEntry(val result: LabResult, val shown: String)

@Composable
private fun LabPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    var sexIndex by rememberSaveable { mutableStateOf(0) }
    var age by rememberSaveable { mutableStateOf("") }
    var analyteIndex by rememberSaveable { mutableStateOf(3) }
    var unitIndex by rememberSaveable { mutableStateOf(0) }
    var value by rememberSaveable { mutableStateOf("") }
    var refLow by rememberSaveable { mutableStateOf("") }
    var refHigh by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<LabEntry>() }
    val analyte = Analyte.entries[analyteIndex]
    val unit = analyte.units[unitIndex.coerceIn(0, analyte.units.lastIndex)]

    InfoCard {
        ChoiceChips(listOf(stringResource(R.string.sex_female), stringResource(R.string.sex_male)), sexIndex) { sexIndex = it }
        NumField(stringResource(R.string.age_years), age, { age = it }, Modifier.fillMaxWidth())
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(analyteName(analyte))) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Analyte.entries.forEachIndexed { i, a ->
                    DropdownMenuItem(text = { Text(stringResource(analyteName(a))) }, onClick = { analyteIndex = i; unitIndex = 0; menu = false })
                }
            }
        }
        ChoiceChips(analyte.units.map { it.symbol }, unitIndex) { unitIndex = it }
        NumField("${stringResource(R.string.lab_value)} (${unit.symbol})", value, { value = it }, Modifier.fillMaxWidth())
        Text(stringResource(R.string.lab_report_range), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.lab_ref_low), refLow, { refLow = it }, Modifier.weight(1f))
            NumField(stringResource(R.string.lab_ref_high), refHigh, { refHigh = it }, Modifier.weight(1f))
        }
        Button(enabled = value.num() != null, onClick = {
            val v = value.num() ?: return@Button
            val r = LabReference.interpret(
                analyte, unit.toBase(v), if (sexIndex == 1) Sex.MALE else Sex.FEMALE, age.num()?.toInt(),
                refLow.num()?.let { unit.toBase(it) }, refHigh.num()?.let { unit.toBase(it) },
            )
            entries.add(0, LabEntry(r, "${fmt(v, 2)} ${unit.symbol}"))
            value = ""; refLow = ""; refHigh = ""
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.interpret)) }
    }

    entries.forEach { e -> LabResultCard(tool, e, save) }
    if (entries.isNotEmpty()) TextButton(onClick = { entries.clear() }) { Text(stringResource(R.string.reset)) }
    Note(R.string.lab_sources)
}

@Composable
private fun LabResultCard(tool: Tool, e: LabEntry, save: (com.sensora.app.data.MeasurementRecord) -> Unit) {
    val r = e.result
    val base = r.analyte.baseUnit
    ResultCard(stringResource(analyteName(r.analyte))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.shown, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(when (r.status) { LabStatus.LOW -> R.string.status_low; LabStatus.NORMAL -> R.string.status_normal; LabStatus.HIGH -> R.string.status_high }),
                color = urgencyColor(if (r.status == LabStatus.NORMAL) Urgency.NORMAL else Urgency.DISCUSS), fontWeight = FontWeight.Bold)
        }
        val rangeText = listOfNotNull(r.low?.let { "≥ ${fmt(it, 2)}" }, r.high?.let { "≤ ${fmt(it, 2)}" }).joinToString("  ")
        if (rangeText.isNotEmpty()) Text(stringResource(if (r.usedReportRange) R.string.lab_range_report else R.string.lab_range_typical, "$rangeText $base"),
            style = MaterialTheme.typography.bodySmall)
        r.category?.let { Text(stringResource(categoryName(it)), fontWeight = FontWeight.SemiBold) }
        if (r.egfr != null && r.egfrStage != null) {
            InfoRow("eGFR (CKD-EPI 2021)", "${fmt(r.egfr, 0)} mL/min/1.73 m²")
            Text(stringResource(R.string.egfr_stage, r.egfrStage.name.replace("A", "a").replace("B", "b")), fontWeight = FontWeight.SemiBold)
            Note(R.string.egfr_note)
        }
        Text(stringResource(analyteInfo(r.analyte)), style = MaterialTheme.typography.bodyMedium)
        UrgencyBanner(r.urgency)
        SaveButton { note -> save(tool.record(r.value, base, Quality.GOOD, "lab_report_interpretation",
            details = "${r.analyte.name} ${r.status.name} ${r.category?.name ?: ""} ${r.urgency.name}", note = note)) }
    }
}

// ------------------------------------------------------------ Blood pressure & pulse
@Composable
private fun BpPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    var sys by rememberSaveable { mutableStateOf("") }
    var dia by rememberSaveable { mutableStateOf("") }
    var pulse by rememberSaveable { mutableStateOf("") }
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.bp_sys), sys, { sys = it }, Modifier.weight(1f))
            NumField(stringResource(R.string.bp_dia), dia, { dia = it }, Modifier.weight(1f))
            NumField(stringResource(R.string.bp_pulse), pulse, { pulse = it }, Modifier.weight(1f))
        }
        Note(R.string.bp_how_to_measure)
    }
    val s = sys.num()?.toInt(); val d = dia.num()?.toInt()
    if (s != null && d != null && s in 50..300 && d in 30..200 && s > d) {
        val r = BloodPressure.classify(s, d, pulse.num()?.toInt())
        ResultCard("$s/$d mmHg") {
            Text(stringResource(when (r.category) {
                BpCategory.LOW -> R.string.bp_low; BpCategory.OPTIMAL -> R.string.bp_optimal; BpCategory.NORMAL -> R.string.bp_normal
                BpCategory.HIGH_NORMAL -> R.string.bp_high_normal; BpCategory.GRADE_1 -> R.string.bp_grade1
                BpCategory.GRADE_2 -> R.string.bp_grade2; BpCategory.GRADE_3 -> R.string.bp_grade3
            }), style = MaterialTheme.typography.titleMedium)
            if (r.isolatedSystolic) Note(R.string.bp_isolated)
            UrgencyBanner(r.urgency)
            if (r.category == BpCategory.GRADE_3) WarningCard(stringResource(R.string.bp_crisis), isError = true)
            if (r.category == BpCategory.LOW) Note(R.string.bp_low_note)
            Note(R.string.bp_home_note)
            r.pulseUrgency?.let { pu ->
                HorizontalDivider()
                Text("${stringResource(R.string.bp_pulse)}: ${pulse.num()?.toInt()} /min", fontWeight = FontWeight.SemiBold)
                Note(if (pu == Urgency.NORMAL) R.string.pulse_normal else R.string.pulse_abnormal)
            }
            SaveButton { note -> save(tool.record(s.toDouble(), "mmHg", Quality.GOOD, "esh_2023", details = "$s/$d ${r.category.name}", note = note)) }
        }
    }
}

// ------------------------------------------------------------ SpO2
@Composable
private fun Spo2Panel(tool: Tool) {
    val save = rememberMeasurementSaver()
    var spo2 by rememberSaveable { mutableStateOf("") }
    InfoCard { NumField(stringResource(R.string.spo2_value), spo2, { spo2 = it }, Modifier.fillMaxWidth()) }
    val v = spo2.num()?.toInt()
    if (v != null && v in 50..100) {
        val u = Oximetry.urgency(v)
        ResultCard("SpO₂ $v %") {
            UrgencyBanner(u)
            Note(R.string.spo2_copd)
            Note(R.string.spo2_accuracy)
            SaveButton { note -> save(tool.record(v.toDouble(), "%", Quality.GOOD, "bts_oximetry", note = note)) }
        }
    }
}

// ------------------------------------------------------------ Spirometry & peak flow
@Composable
private fun LungPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    val fields = remember { mutableStateListOf("", "", "", "", "", "", "", "", "", "", "") }
    val labels = listOf(R.string.sp_fev1, R.string.sp_fvc, R.string.sp_ratio, R.string.sp_fev1_pct, R.string.sp_fvc_pct,
        R.string.sp_fev1_z, R.string.sp_fvc_z, R.string.sp_ratio_z, R.string.sp_ratio_lln, R.string.sp_post_fev1, R.string.sp_fev1_pred)
    var pef by rememberSaveable { mutableStateOf("") }
    var best by rememberSaveable { mutableStateOf("") }

    InfoCard {
        Text(stringResource(R.string.sp_title), style = MaterialTheme.typography.titleMedium)
        Note(R.string.sp_fill_hint)
        labels.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { k, label ->
                    val idx = row * 2 + k
                    NumField(stringResource(label), fields[idx], { fields[idx] = it }, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    val f = fields.map { it.num() }
    val input = SpiroInput(fev1 = f[0], fvc = f[1], ratio = f[2], fev1Pct = f[3], fvcPct = f[4], fev1Z = f[5], fvcZ = f[6],
        ratioZ = f[7], ratioLln = f[8], postFev1 = f[9], fev1Predicted = f[10])
    val r = Spirometry.interpret(input)
    if (r.pattern != SpiroPattern.INSUFFICIENT_DATA) {
        ResultCard(stringResource(R.string.sp_title)) {
            r.ratio?.let { InfoRow("FEV1/FVC", fmt(it, 2)) }
            Text(stringResource(when (r.pattern) {
                SpiroPattern.NORMAL -> R.string.sp_normal; SpiroPattern.OBSTRUCTION -> R.string.sp_obstruction
                SpiroPattern.LOW_FVC -> R.string.sp_low_fvc; SpiroPattern.OBSTRUCTION_WITH_LOW_FVC -> R.string.sp_mixed
                SpiroPattern.INSUFFICIENT_DATA -> R.string.sp_insufficient
            }), style = MaterialTheme.typography.titleMedium)
            r.severity?.let { Text(stringResource(when (it) { SpiroSeverity.MILD -> R.string.sp_sev_mild; SpiroSeverity.MODERATE -> R.string.sp_sev_moderate; SpiroSeverity.SEVERE -> R.string.sp_sev_severe })) }
            r.goldGrade?.let { Text(stringResource(R.string.sp_gold, it)) }
            r.bronchodilatorSignificant?.let { Text(stringResource(if (it) R.string.sp_bd_yes else R.string.sp_bd_no)) }
            Note(when (r.method) { SpiroMethod.Z_SCORE -> R.string.sp_method_z; SpiroMethod.LLN -> R.string.sp_method_lln; else -> R.string.sp_method_fixed })
            UrgencyBanner(r.urgency)
            SaveButton { note -> save(tool.record(r.ratio ?: 0.0, "FEV1/FVC", Quality.GOOD, "ats_ers_2022", details = "${r.pattern.name} ${r.goldGrade?.let { "GOLD $it" } ?: ""}", note = note)) }
        }
    }

    InfoCard {
        Text(stringResource(R.string.pef_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.pef_value), pef, { pef = it }, Modifier.weight(1f))
            NumField(stringResource(R.string.pef_best), best, { best = it }, Modifier.weight(1f))
        }
    }
    val p = pef.num(); val b = best.num()
    if (p != null && b != null && b > 0) {
        val z = PeakFlow.zone(p, b)
        ResultCard("${fmt(p / b * 100, 0)} %") {
            Text(stringResource(when (z) { PefZone.GREEN -> R.string.pef_green; PefZone.YELLOW -> R.string.pef_yellow; PefZone.RED -> R.string.pef_red }),
                fontWeight = FontWeight.SemiBold)
            UrgencyBanner(PeakFlow.urgency(z))
        }
    }
}

// ------------------------------------------------------------ Audiogram
@StringRes private fun gradeName(g: HearingGrade): Int = when (g) {
    HearingGrade.NORMAL -> R.string.hg_normal; HearingGrade.MILD -> R.string.hg_mild; HearingGrade.MODERATE -> R.string.hg_moderate
    HearingGrade.MODERATELY_SEVERE -> R.string.hg_mod_severe; HearingGrade.SEVERE -> R.string.hg_severe
    HearingGrade.PROFOUND -> R.string.hg_profound; HearingGrade.COMPLETE -> R.string.hg_complete
}

@Composable
private fun HearingPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    val left = remember { mutableStateListOf("", "", "", "", "", "") }
    val right = remember { mutableStateListOf("", "", "", "", "", "") }
    InfoCard {
        Text(stringResource(R.string.au_title), style = MaterialTheme.typography.titleMedium)
        Note(R.string.au_hint)
        Audiogram.FREQUENCIES.forEachIndexed { i, hz ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", modifier = Modifier.width(64.dp))
                NumField(stringResource(R.string.ear_right), right[i], { right[i] = it }, Modifier.weight(1f))
                NumField(stringResource(R.string.ear_left), left[i], { left[i] = it }, Modifier.weight(1f))
            }
        }
    }
    val r = Audiogram.interpret(EarThresholds(left.map { it.num() }), EarThresholds(right.map { it.num() }))
    if (r.leftPta != null || r.rightPta != null) {
        ResultCard(stringResource(R.string.au_title)) {
            r.rightPta?.let { InfoRow(stringResource(R.string.au_pta_right), "${fmt(it, 1)} dB HL · ${stringResource(gradeName(r.rightGrade!!))}") }
            r.leftPta?.let { InfoRow(stringResource(R.string.au_pta_left), "${fmt(it, 1)} dB HL · ${stringResource(gradeName(r.leftGrade!!))}") }
            r.overall?.let { Text(stringResource(R.string.au_overall, stringResource(gradeName(it))), style = MaterialTheme.typography.titleMedium) }
            if (r.unilateral) Text(stringResource(R.string.au_unilateral), fontWeight = FontWeight.SemiBold)
            if (r.asymmetric) Text(stringResource(R.string.au_asymmetric), fontWeight = FontWeight.SemiBold)
            if (r.noiseNotchLeft || r.noiseNotchRight) Text(stringResource(R.string.au_notch))
            UrgencyBanner(r.urgency)
            WarningCard(stringResource(R.string.au_sudden))
            SaveButton { note -> save(tool.record(listOfNotNull(r.leftPta, r.rightPta).min(), "dB HL", Quality.GOOD, "who_2021",
                details = "R ${r.rightPta?.let { fmt(it, 0) }} L ${r.leftPta?.let { fmt(it, 0) }} ${r.overall?.name}", note = note)) }
        }
    }
}

// ------------------------------------------------------------ Vision
@Composable
private fun VisionPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = 6/x, 1 = 20/x, 2 = decimal, 3 = logMAR
    var right by rememberSaveable { mutableStateOf("") }
    var left by rememberSaveable { mutableStateOf("") }
    val rx = remember { mutableStateListOf("", "", "", "", "", "") } // R sph, cyl, add; L sph, cyl, add

    fun acuity(s: String): Acuity? {
        val v = s.num() ?: return null
        return when (mode) {
            0 -> if (v > 0) Vision.fromSnellen(6.0, v) else null
            1 -> if (v > 0) Vision.fromSnellen(20.0, v) else null
            2 -> if (v in 0.01..2.5) Acuity(v) else null
            else -> if (v in -0.5..2.0) Vision.fromLogMar(v) else null
        }
    }

    InfoCard {
        Text(stringResource(R.string.va_title), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(listOf("6/…", "20/…", stringResource(R.string.va_decimal), "logMAR"), mode) { mode = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.eye_right), right, { right = it }, Modifier.weight(1f))
            NumField(stringResource(R.string.eye_left), left, { left = it }, Modifier.weight(1f))
        }
        Note(R.string.va_hint)
    }
    val ar = acuity(right); val al = acuity(left)
    val better = listOfNotNull(ar, al).maxByOrNull { it.decimal }
    if (better != null) {
        val cat = Vision.category(better)
        ResultCard(stringResource(R.string.va_title)) {
            ar?.let { InfoRow(stringResource(R.string.eye_right), "${it.snellen6} · ${it.snellen20} · ${fmt(it.decimal, 2)} · logMAR ${fmt(it.logMar, 2)}") }
            al?.let { InfoRow(stringResource(R.string.eye_left), "${it.snellen6} · ${it.snellen20} · ${fmt(it.decimal, 2)} · logMAR ${fmt(it.logMar, 2)}") }
            Text(stringResource(when (cat) {
                VisionCategory.NO_IMPAIRMENT -> R.string.vi_none; VisionCategory.MILD -> R.string.vi_mild; VisionCategory.MODERATE -> R.string.vi_moderate
                VisionCategory.SEVERE -> R.string.vi_severe; VisionCategory.BLINDNESS -> R.string.vi_blind
            }), style = MaterialTheme.typography.titleMedium)
            if (ar != null && al != null && kotlin.math.abs(ar.logMar - al.logMar) >= 0.2) Text(stringResource(R.string.va_difference))
            UrgencyBanner(Vision.urgency(cat))
            Note(R.string.va_sudden)
            SaveButton { note -> save(tool.record(better.logMar, "logMAR", Quality.GOOD, "icd11_visual_acuity", details = cat.name, note = note)) }
        }
    }

    InfoCard {
        Text(stringResource(R.string.rx_title), style = MaterialTheme.typography.titleMedium)
        listOf(R.string.eye_right to 0, R.string.eye_left to 3).forEach { (label, o) ->
            Text(stringResource(label), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("SPH", rx[o], { rx[o] = it }, Modifier.weight(1f))
                NumField("CYL", rx[o + 1], { rx[o + 1] = it }, Modifier.weight(1f))
                NumField("ADD", rx[o + 2], { rx[o + 2] = it }, Modifier.weight(1f))
            }
        }
    }
    listOf(R.string.eye_right to 0, R.string.eye_left to 3).forEach { (label, o) ->
        val sph = rx[o].num()
        if (sph != null) {
            val p = Prescription.interpret(sph, rx[o + 1].num() ?: 0.0, rx[o + 2].num() ?: 0.0)
            ResultCard(stringResource(label)) {
                InfoRow(stringResource(R.string.rx_se), "${fmt(p.sphericalEquivalent, 2)} D")
                Text(stringResource(when (p.refraction) {
                    Refraction.NONE -> R.string.rx_none; Refraction.MYOPIA -> R.string.rx_myopia; Refraction.HIGH_MYOPIA -> R.string.rx_high_myopia
                    Refraction.HYPEROPIA -> R.string.rx_hyperopia; Refraction.HIGH_HYPEROPIA -> R.string.rx_high_hyperopia
                }), fontWeight = FontWeight.SemiBold)
                if (p.astigmatism) Text(stringResource(R.string.rx_astigmatism))
                if (p.presbyopiaAdd) Text(stringResource(R.string.rx_add))
                if (p.urgency != Urgency.NORMAL) UrgencyBanner(p.urgency)
            }
        }
    }
    Note(R.string.rx_note)
}
