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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlin.math.abs

@Composable
fun InclinometerScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.accel_unavailable)) }
            return@ToolScaffold
        }
        val sample by remember(sm) { SensorReaders.levelSamples(sm) }.collectAsStateWithLifecycle(initialValue = null)
        var zero by rememberSaveable { mutableStateOf<Double?>(null) }
        var a by rememberSaveable { mutableStateOf<Double?>(null) }
        var b by rememberSaveable { mutableStateOf<Double?>(null) }
        val save = rememberMeasurementSaver()

        ScreenColumn(padding) {
            val s = sample
            if (s == null) {
                WaitingCard()
                return@ScreenColumn
            }
            val raw = OrientationMath.totalTilt(s.gx, s.gy, s.gz)
            val angle = raw - (zero ?: 0.0)
            val quality = QualityRules.stability(s.stability)

            ArcGauge(angle.toFloat(), Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
            BigValue("${fmt(angle, 1)}°", "", caption = stringResource(R.string.incline_angle))
            CenterRow { QualityBadge(quality) }
            InfoCard {
                InfoRow(stringResource(R.string.incline_slope), formatSlope(angle, 1))
                InfoRow(stringResource(R.string.unit_mmm), formatSlope(angle, 2))
            }
            CenterRow {
                OutlinedButton(onClick = { zero = raw }) { Text(stringResource(R.string.incline_zero)) }
                if (zero != null) TextButton(onClick = { zero = null }) { Text(stringResource(R.string.incline_clear_zero)) }
            }
            CenterRow {
                OutlinedButton(onClick = { a = angle }) { Text(stringResource(R.string.incline_set_a) + (a?.let { " (${fmt(it, 1)}°)" } ?: "")) }
                OutlinedButton(onClick = { b = angle }) { Text(stringResource(R.string.incline_set_b) + (b?.let { " (${fmt(it, 1)}°)" } ?: "")) }
            }
            val diff = if (a != null && b != null) b!! - a!! else null
            if (diff != null) InfoCard { InfoRow(stringResource(R.string.incline_diff), "${fmt(diff, 1)}°") }
            SaveButton { note ->
                save(tool.record(angle, "°", quality, if (zero != null) "gravity_relative" else "gravity_absolute",
                    details = diff?.let { "A→B ${fmt(it, 1)}°" }, note = note))
            }
        }
    }
}

@Composable
private fun ArcGauge(angle: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.aspectRatio(2f)) {
        val stroke = size.width * 0.06f
        val d = size.width - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        drawArc(track, 180f, 180f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, 180f, (abs(angle).coerceAtMost(90f) / 90f) * 180f, false, topLeft, Size(d, d),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
