package com.sensora.app.ui.health

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.health.Acuity
import com.sensora.app.core.health.Urgency
import com.sensora.app.core.health.Vision
import com.sensora.app.core.health.VisionCategory
import com.sensora.app.core.model.Quality
import com.sensora.app.core.vision.ColorScience
import com.sensora.app.core.vision.ColorScoring
import com.sensora.app.core.vision.ContrastScorer
import com.sensora.app.core.vision.Direction
import com.sensora.app.core.vision.EtdrsScorer
import com.sensora.app.core.vision.Optotype
import com.sensora.app.core.vision.PlateGenerator
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlin.math.tan
import kotlin.random.Random

private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Full brightness and screen kept on during vision tests, restored afterwards. */
@Composable
private fun BrightScreen() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        val w = activity.window
        val old = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = 1f }
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            w.attributes = w.attributes.apply { screenBrightness = old }
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun rememberPxPerMm(): Float {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    return prefs.pxPerMm ?: (ctx.resources.displayMetrics.xdpi / 25.4f)
}

/** Tumbling E on a 5 × 5 grid; opening points in [dir]. */
private fun DrawScope.tumblingE(center: Offset, sizePx: Float, dir: Direction, color: Color) {
    val s = sizePx; val u = s / 5f
    val left = center.x - s / 2; val top = center.y - s / 2
    val angle = when (dir) { Direction.RIGHT -> 0f; Direction.DOWN -> 90f; Direction.LEFT -> 180f; Direction.UP -> 270f }
    rotate(angle, center) {
        drawRect(color, Offset(left, top), Size(u, s))
        for (row in listOf(0, 2, 4)) drawRect(color, Offset(left, top + row * u), Size(s, u))
    }
}

@Composable
private fun DirectionPad(onAnswer: (Direction?) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalIconButton(onClick = { onAnswer(Direction.UP) }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.ArrowUpward, contentDescription = "↑") }
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            FilledTonalIconButton(onClick = { onAnswer(Direction.LEFT) }, modifier = Modifier.size(64.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "←") }
            FilledTonalIconButton(onClick = { onAnswer(Direction.RIGHT) }, modifier = Modifier.size(64.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "→") }
        }
        FilledTonalIconButton(onClick = { onAnswer(Direction.DOWN) }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.ArrowDownward, contentDescription = "↓") }
        TextButton(onClick = { onAnswer(null) }) { Text(stringResource(R.string.vs_cant_see)) }
    }
}

@Composable
fun VisionScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        var tab by rememberSaveable { mutableStateOf(0) }
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.vs_disclaimer))
            ChoiceChips(listOf(stringResource(R.string.vs_tab_calibrate), stringResource(R.string.vs_tab_acuity), stringResource(R.string.vs_tab_contrast),
                stringResource(R.string.vs_tab_color), stringResource(R.string.vs_tab_amsler)), tab) { tab = it }
            when (tab) {
                0 -> CalibrationPanel()
                1 -> AcuityPanel(tool)
                2 -> ContrastPanel(tool)
                3 -> ColorPanel(tool)
                else -> AmslerPanel(tool)
            }
        }
    }
}

// ------------------------------------------------------------ Screen calibration
@Composable
private fun CalibrationPanel() {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    val dpiEstimate = ctx.resources.displayMetrics.xdpi / 25.4f
    var pxPerMm by remember { mutableStateOf(prefs.pxPerMm ?: dpiEstimate) }
    val density = LocalDensity.current.density
    val savedMsg = stringResource(R.string.vs_cal_saved)
    InfoCard {
        Text(stringResource(R.string.vs_cal_steps), style = MaterialTheme.typography.bodyMedium)
        val widthPx = 85.6f * pxPerMm
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(width = (widthPx / density).dp, height = (53.98f * pxPerMm / density).dp)
                .background(MaterialTheme.colorScheme.primaryContainer))
        }
        Slider(value = pxPerMm, onValueChange = { pxPerMm = it }, valueRange = dpiEstimate * 0.7f..dpiEstimate * 1.3f)
        InfoRow(stringResource(R.string.vs_cal_value), "${fmt(pxPerMm.toDouble(), 2)} px/mm")
        Button(onClick = { prefs.pxPerMm = pxPerMm; android.widget.Toast.makeText(ctx, savedMsg, android.widget.Toast.LENGTH_SHORT).show() }) {
            Text(stringResource(R.string.save))
        }
        Note(R.string.vs_cal_note)
    }
}

// ------------------------------------------------------------ Visual acuity (ETDRS method, tumbling E)
private val DISTANCES_MM = listOf(400.0, 1000.0, 3000.0)

@Composable
private fun AcuityPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    val pxPerMm = rememberPxPerMm()
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    var distIndex by rememberSaveable { mutableStateOf(0) }
    var eye by rememberSaveable { mutableStateOf(0) }
    var scorer by remember { mutableStateOf<EtdrsScorer?>(null) }
    var dir by remember { mutableStateOf(Direction.RIGHT) }
    var tick by remember { mutableStateOf(0) }
    var resolutionLimited by remember { mutableStateOf(false) }
    val distance = DISTANCES_MM[distIndex]
    val maxPx = minOf(config.screenWidthDp, config.screenHeightDp) * density * 0.55f
    fun px(line: Int) = (Optotype.letterHeightMm(line / 10.0, distance) * pxPerMm).toFloat()
    // Largest line that still fits on screen (max logMAR 1.0).
    val maxLine = (10 downTo -3).firstOrNull { px(it) <= maxPx } ?: -3

    val s = scorer
    if (s == null || s.done) {
        InfoCard {
            Text(stringResource(R.string.vs_acuity_steps), style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(listOf("40 cm", "1 m", "3 m"), distIndex) { distIndex = it }
            ChoiceChips(listOf(stringResource(R.string.eye_right), stringResource(R.string.eye_left), stringResource(R.string.vs_both_eyes)), eye) { eye = it }
            if (distIndex == 2) Note(R.string.vs_helper_note)
            Button(onClick = {
                resolutionLimited = false
                scorer = EtdrsScorer(start = minOf(7, maxLine), maxLine = maxLine); dir = Direction.entries.random(); tick++
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vs_start)) }
        }
        if (s != null && s.done) AcuityResult(tool, s.score, distance, eye, resolutionLimited, save)
        return
    }
    BrightScreen()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val sizePx = px(s.line)
    Text(stringResource(R.string.vs_line, fmt(s.line / 10.0, 1)), style = MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth().height((maxPx * 1.4f / density).dp).background(Color.White), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            tumblingE(center, sizePx, dir, Color.Black)
            // Crowding frame at one letter width, as in crowded single-optotype tests.
            val half = sizePx * 1.5f
            drawRect(Color.Black, Offset(center.x - half, center.y - half), Size(2 * half, 2 * half), style = Stroke(sizePx / 5f))
        }
    }
    DirectionPad { answer ->
        s.record(answer == dir)
        if (!s.done && px(s.line) < 10f) { resolutionLimited = true; s.stop() }
        var next = Direction.entries.random()
        while (next == dir) next = Direction.entries.random()
        dir = next; tick++
    }
}

@Composable
private fun AcuityResult(tool: Tool, logMar: Double, distanceMm: Double, eye: Int, limited: Boolean, save: (com.sensora.app.data.MeasurementRecord) -> Unit) {
    val a = Vision.fromLogMar(logMar)
    val isDistance = distanceMm >= 3000.0
    ResultCard(stringResource(R.string.vs_acuity_result)) {
        BigValue(fmt(logMar, 2), "logMAR")
        InfoRow("Snellen", "${a.snellen6} · ${a.snellen20}")
        InfoRow(stringResource(R.string.va_decimal), fmt(a.decimal, 2))
        if (limited) Note(R.string.vs_resolution_limit)
        if (isDistance) {
            val cat = Vision.category(a)
            Text(stringResource(when (cat) {
                VisionCategory.NO_IMPAIRMENT -> R.string.vi_none; VisionCategory.MILD -> R.string.vi_mild; VisionCategory.MODERATE -> R.string.vi_moderate
                VisionCategory.SEVERE -> R.string.vi_severe; VisionCategory.BLINDNESS -> R.string.vi_blind
            }), fontWeight = FontWeight.SemiBold)
            UrgencyBanner(Vision.urgency(cat))
        } else {
            Text(stringResource(if (distanceMm < 1000) R.string.vs_near_result else R.string.vs_intermediate_result), fontWeight = FontWeight.SemiBold)
            UrgencyBanner(if (logMar > 0.3) Urgency.DISCUSS else Urgency.NORMAL)
        }
        Note(R.string.vs_acuity_note)
        Note(R.string.va_sudden)
        SaveButton { note -> save(tool.record(logMar, "logMAR", Quality.LIMITED, "etdrs_tumbling_e",
            details = "${(distanceMm / 10).toInt()} cm · eye ${listOf("R", "L", "OU")[eye]}", note = note)) }
    }
}

// ------------------------------------------------------------ Contrast sensitivity (Pelli–Robson method)
@Composable
private fun ContrastPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    val pxPerMm = rememberPxPerMm()
    var scorer by remember { mutableStateOf<ContrastScorer?>(null) }
    var dir by remember { mutableStateOf(Direction.UP) }
    var tick by remember { mutableStateOf(0) }
    val density = LocalDensity.current.density
    // Pelli–Robson letters subtend about 3° at the viewing distance (40 cm here).
    val sizePx = (2 * 400.0 * tan(Math.toRadians(1.5)) * pxPerMm).toFloat()

    val s = scorer
    if (s == null || s.done) {
        InfoCard {
            Text(stringResource(R.string.vs_contrast_steps), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { scorer = ContrastScorer(); dir = Direction.entries.random(); tick++ }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vs_start))
            }
        }
        if (s != null && s.done) {
            val cat = ContrastScorer.interpretation(s.logCs)
            ResultCard(stringResource(R.string.vs_contrast_result)) {
                BigValue(fmt(s.logCs, 2), "log CS")
                Text(stringResource(when (cat) { 0 -> R.string.vs_cs_normal; 1 -> R.string.vs_cs_borderline; else -> R.string.vs_cs_reduced }), fontWeight = FontWeight.SemiBold)
                UrgencyBanner(if (cat == 2) Urgency.DISCUSS else Urgency.NORMAL)
                Note(R.string.vs_contrast_note)
                SaveButton { note -> save(tool.record(s.logCs, "logCS", Quality.LIMITED, "pelli_robson_method", note = note)) }
            }
        }
        return
    }
    BrightScreen()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val grey = ColorScience.greyForContrast(s.contrast)
    Text(stringResource(R.string.vs_contrast_level, fmt(0.15 * s.level, 2)), style = MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth().height((sizePx * 2f / density).dp).background(Color.White)) {
        Canvas(Modifier.fillMaxSize()) { tumblingE(center, sizePx, dir, Color(grey, grey, grey)) }
    }
    DirectionPad { answer ->
        s.record(answer == dir)
        // Stop when the screen cannot show a fainter grey than white.
        if (!s.done && ColorScience.greyForContrast(s.contrast) >= 255) s.stop()
        dir = Direction.entries.random(); tick++
    }
}

// ------------------------------------------------------------ Colour vision (generated pseudo-isochromatic plates)
@Composable
private fun ColorPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    var seed by remember { mutableStateOf(0) }
    val plates = remember(seed) { if (seed == 0) emptyList() else PlateGenerator.series(seed) }
    val answers = remember(seed) { mutableStateListOf<Int?>() }

    if (plates.isEmpty() || answers.size >= plates.size) {
        InfoCard {
            Text(stringResource(R.string.vs_color_steps), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { seed = Random.nextInt(1, Int.MAX_VALUE) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vs_start)) }
        }
        if (plates.isNotEmpty()) {
            val r = ColorScoring.score(plates, answers)
            ResultCard(stringResource(R.string.vs_color_result)) {
                Text(stringResource(when {
                    !r.valid -> R.string.vs_color_invalid
                    r.redGreenSuspected && r.tritanSuspected -> R.string.vs_color_both
                    r.redGreenSuspected -> R.string.vs_color_rg
                    r.tritanSuspected -> R.string.vs_color_tritan
                    else -> R.string.vs_color_normal
                }), fontWeight = FontWeight.SemiBold)
                InfoRow(stringResource(R.string.vs_color_errors), "${r.redGreenErrors}/6 · ${r.tritanErrors}/2 · ${r.controlErrors}/2")
                UrgencyBanner(if (r.valid && (r.redGreenSuspected || r.tritanSuspected)) Urgency.DISCUSS else Urgency.NORMAL)
                Note(R.string.vs_color_note)
                SaveButton { note -> save(tool.record((r.redGreenErrors + r.tritanErrors).toDouble(), "errors", Quality.LIMITED, "generated_pip_plates",
                    details = "RG ${r.redGreenErrors}/6 T ${r.tritanErrors}/2 C ${r.controlErrors}/2", note = note)) }
            }
        }
        return
    }
    BrightScreen()
    val plate = plates[answers.size]
    Text(stringResource(R.string.vs_plate, answers.size + 1, plates.size), style = MaterialTheme.typography.labelLarge)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val d = size.minDimension
        drawCircle(Color(0xFFF2F0EA), d / 2, center)
        plate.dots.forEach { dot -> drawCircle(Color(dot.rgb), dot.r * d, Offset(dot.x * d, dot.y * d)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf(2, 3, 4, 5), listOf(6, 7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { n -> FilledTonalButton(onClick = { answers.add(n) }, modifier = Modifier.weight(1f)) { Text("$n") } }
            }
        }
        TextButton(onClick = { answers.add(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vs_no_number)) }
    }
}

// ------------------------------------------------------------ Amsler grid
@Composable
private fun AmslerPanel(tool: Tool) {
    val save = rememberMeasurementSaver()
    val pxPerMm = rememberPxPerMm()
    val density = LocalDensity.current.density
    var eye by rememberSaveable { mutableStateOf(0) }
    val checks = remember { mutableStateListOf(false, false, false) }
    BrightScreen()
    InfoCard {
        Text(stringResource(R.string.vs_amsler_steps), style = MaterialTheme.typography.bodyMedium)
        ChoiceChips(listOf(stringResource(R.string.eye_right), stringResource(R.string.eye_left)), eye) { eye = it; for (i in 0..2) checks[i] = false }
    }
    // 10 × 10 squares of 5 mm (about 1° each at 30 cm).
    val cell = 5f * pxPerMm
    Box(Modifier.fillMaxWidth().height((cell * 10.5f / density).dp).background(Color.White), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((cell * 10 / density).dp)) {
            for (i in 0..10) {
                drawLine(Color.Black, Offset(i * cell, 0f), Offset(i * cell, 10 * cell), 2f)
                drawLine(Color.Black, Offset(0f, i * cell), Offset(10 * cell, i * cell), 2f)
            }
            drawCircle(Color.Black, cell * 0.18f, Offset(5 * cell, 5 * cell))
        }
    }
    InfoCard {
        listOf(R.string.vs_amsler_q1, R.string.vs_amsler_q2, R.string.vs_amsler_q3).forEachIndexed { i, q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checks[i], onCheckedChange = { checks[i] = it })
                Text(stringResource(q), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val abnormal = checks.any { it }
        Text(stringResource(if (abnormal) R.string.vs_amsler_abnormal else R.string.vs_amsler_normal), fontWeight = FontWeight.SemiBold)
        UrgencyBanner(if (abnormal) Urgency.SOON else Urgency.NORMAL)
        Note(R.string.vs_amsler_note)
        SaveButton { note -> save(tool.record(if (abnormal) 1.0 else 0.0, "", Quality.LIMITED, "amsler_grid",
            details = "eye ${if (eye == 0) "R" else "L"} · ${checks.joinToString("") { if (it) "1" else "0" }}", note = note)) }
    }
}
