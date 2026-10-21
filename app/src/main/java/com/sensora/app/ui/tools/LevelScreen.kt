package com.sensora.app.ui.tools

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import com.sensora.app.ui.theme.GoodColor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

@Composable
fun LevelScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.accel_unavailable)) }
            return@ToolScaffold
        }
        val prefs = LocalContext.current.sensoraApp.prefs
        val sample by remember(sm) { SensorReaders.levelSamples(sm) }.collectAsStateWithLifecycle(initialValue = null)
        var mode by rememberSaveable { mutableStateOf(0) }
        var unit by rememberSaveable { mutableStateOf(0) }
        var offX by remember { mutableStateOf(prefs.levelOffsetX) }
        var offY by remember { mutableStateOf(prefs.levelOffsetY) }
        var offV by remember { mutableStateOf(prefs.levelOffsetV) }
        var autoCapture by rememberSaveable { mutableStateOf(false) }
        val save = rememberMeasurementSaver()

        ScreenColumn(padding) {
            ChoiceChips(listOf(stringResource(R.string.level_surface), stringResource(R.string.level_vertical)), mode) { mode = it }
            val s = sample
            if (s == null) {
                WaitingCard()
                return@ScreenColumn
            }
            val quality = QualityRules.stability(s.stability)
            val stable = quality == Quality.GOOD
            val x = s.tilt.rollDeg - offX
            val y = s.tilt.pitchDeg - offY
            val dev = OrientationMath.verticalDeviation(s.gx, s.gy) - offV

            if (mode == 0) {
                val isLevel = abs(x) < 0.3 && abs(y) < 0.3
                BubbleLevel(x.toFloat(), y.toFloat(), isLevel, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
                Text(stringResource(if (isLevel) R.string.level_is_level else R.string.level_not_level),
                    style = MaterialTheme.typography.titleLarge, color = if (isLevel) GoodColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                InfoCard {
                    InfoRow(stringResource(R.string.level_x), formatSlope(x, unit))
                    InfoRow(stringResource(R.string.level_y), formatSlope(y, unit))
                }
                if (abs(s.gz) < 7.0) WarningCard(stringResource(R.string.level_place_flat))
            } else {
                PlumbView(s.gx.toFloat(), s.gy.toFloat(), abs(dev) < 0.3, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
                BigValue(formatSlope(dev, unit), "", caption = stringResource(R.string.level_deviation))
                if (abs(s.gz) > 7.0) WarningCard(stringResource(R.string.level_place_edge))
            }

            ChoiceChips(listOf(stringResource(R.string.unit_deg), stringResource(R.string.unit_percent), stringResource(R.string.unit_mmm)), unit) { unit = it }
            CenterRow {
                QualityBadge(quality)
                Text(stringResource(if (stable) R.string.level_stable else R.string.level_unstable))
            }
            CenterRow {
                OutlinedButton(onClick = {
                    if (mode == 0) {
                        offX = s.tilt.rollDeg; offY = s.tilt.pitchDeg
                        prefs.levelOffsetX = offX; prefs.levelOffsetY = offY
                    } else {
                        offV = OrientationMath.verticalDeviation(s.gx, s.gy); prefs.levelOffsetV = offV
                    }
                }) { Text(stringResource(R.string.calibrate)) }
                TextButton(onClick = {
                    offX = 0.0; offY = 0.0; offV = 0.0
                    prefs.levelOffsetX = 0.0; prefs.levelOffsetY = 0.0; prefs.levelOffsetV = 0.0
                }) { Text(stringResource(R.string.reset)) }
            }
            Text(stringResource(R.string.level_case_hint), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoCapture, onCheckedChange = { autoCapture = it })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.level_auto_capture))
            }

            val makeRecord = { note: String? ->
                if (mode == 0) tool.record(x, "°", quality, "gravity_surface",
                    details = "X ${formatSlope(x, 0)} · Y ${formatSlope(y, 0)}", note = note)
                else tool.record(dev, "°", quality, "gravity_vertical", details = "plumb", note = note)
            }
            LaunchedEffect(autoCapture, stable) {
                if (autoCapture && stable) {
                    save(makeRecord(null))
                    autoCapture = false
                }
            }
            SaveButton { note -> save(makeRecord(note)) }
        }
    }
}

@Composable
private fun BubbleLevel(xDeg: Float, yDeg: Float, isLevel: Boolean, modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val bubble = if (isLevel) GoodColor else MaterialTheme.colorScheme.tertiary
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(bg, r)
        drawCircle(outline, r, style = Stroke(3f))
        drawCircle(outline, r * 0.15f, style = Stroke(2f))
        drawCircle(outline, r * 0.5f, style = Stroke(1f))
        drawLine(outline, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(outline, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        val br = r * 0.12f
        val maxDeg = 10f
        // The bubble moves toward the higher side, like a real spirit level.
        var bx = xDeg / maxDeg * (r - br)
        var by = -yDeg / maxDeg * (r - br)
        val len = hypot(bx, by)
        if (len > r - br) { bx = bx / len * (r - br); by = by / len * (r - br) }
        drawCircle(bubble, br, Offset(c.x + bx, c.y + by))
    }
}

@Composable
private fun PlumbView(gx: Float, gy: Float, ok: Boolean, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val line = if (ok) GoodColor else MaterialTheme.colorScheme.tertiary
    Canvas(modifier.aspectRatio(1f)) {
        val top = Offset(size.width / 2f, size.height * 0.08f)
        drawLine(outline, top, Offset(size.width / 2f, size.height * 0.92f), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        // Down direction in screen coordinates (x right, y down) from the accelerometer's up vector.
        val dx = -gx; val dy = gy
        val n = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val end = Offset(top.x + dx / n * size.height * 0.8f, top.y + dy / n * size.height * 0.8f)
        drawLine(line, top, end, 5f)
        drawCircle(line, size.minDimension * 0.05f, end)
    }
}
