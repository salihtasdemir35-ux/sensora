package com.sensora.app.ui.camera

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.camera.AngleMeasure
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlin.math.abs

fun formatLength(m: Double): String = if (abs(m) < 1.0) "${fmt(m * 100, 1)} cm" else "${fmt(m, 2)} m"

@Composable
fun AngleMeasureScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.accel_unavailable)) }
            return@ToolScaffold
        }
        val gravity by remember(sm) { SensorReaders.gravity(sm) }.collectAsStateWithLifecycle(initialValue = null)
        var eyeHeight by rememberSaveable { mutableStateOf(1.5) }
        var knownDistance by rememberSaveable { mutableStateOf<Double?>(null) }
        var baseAngle by rememberSaveable { mutableStateOf<Double?>(null) }
        var topAngle by rememberSaveable { mutableStateOf<Double?>(null) }
        var editHeight by remember { mutableStateOf(false) }
        var editDistance by remember { mutableStateOf(false) }
        val save = rememberMeasurementSaver()

        ScreenColumn(padding) {
            val g = gravity
            val elevation = g?.let { AngleMeasure.elevationFromGravity(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) }
            val roll = g?.let { OrientationMath.tilt(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()).rollDeg }

            Box(Modifier.fillMaxWidth().height(320.dp)) {
                PermissionGate(CAMERA_PERMISSION, R.string.camera_rationale) {
                    CameraPreview(Modifier.fillMaxSize())
                }
                Crosshair(Modifier.fillMaxSize())
                Text(elevation?.let { "${fmt(it, 1)}°" } ?: "—", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
            if (roll != null && abs(roll) > 8) WarningCard(stringResource(R.string.angle_hold_upright))

            InfoCard {
                InfoRow(stringResource(R.string.angle_eye_height), formatLength(eyeHeight))
                TextButton(onClick = { editHeight = true }) { Text(stringResource(R.string.angle_set_eye_height)) }
                Text(stringResource(R.string.angle_eye_hint), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.angle_steps), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = elevation != null && knownDistance == null, onClick = { baseAngle = elevation }) {
                    Text(stringResource(R.string.angle_mark_base) + (baseAngle?.let { " (${fmt(it, 1)}°)" } ?: ""))
                }
                OutlinedButton(enabled = elevation != null, onClick = { topAngle = elevation }) {
                    Text(stringResource(R.string.angle_mark_top) + (topAngle?.let { " (${fmt(it, 1)}°)" } ?: ""))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editDistance = true }) {
                    Text(knownDistance?.let { stringResource(R.string.angle_known_distance_set, formatLength(it)) } ?: stringResource(R.string.angle_known_distance))
                }
                TextButton(onClick = { baseAngle = null; topAngle = null; knownDistance = null }) { Text(stringResource(R.string.reset)) }
            }

            val result = AngleMeasure.solve(eyeHeight, baseAngle, topAngle, knownDistance)
            val quality = AngleMeasure.quality(result.relativeError)
            if (baseAngle != null && baseAngle!! > -2.0 && knownDistance == null) WarningCard(stringResource(R.string.angle_base_too_flat))
            if (result.distance != null) {
                InfoCard {
                    InfoRow(stringResource(R.string.angle_distance), formatLength(result.distance))
                    result.distanceRange?.let { InfoRow(stringResource(R.string.angle_range), "${formatLength(it.start)} – ${formatLength(it.endInclusive)}") }
                    if (result.height != null) {
                        HorizontalDivider()
                        InfoRow(stringResource(R.string.angle_height), formatLength(result.height))
                        result.heightRange?.let { InfoRow(stringResource(R.string.angle_range), "${formatLength(it.start)} – ${formatLength(it.endInclusive)}") }
                    }
                    QualityBadge(quality)
                    Text(stringResource(R.string.angle_error_note), style = MaterialTheme.typography.bodySmall)
                }
                SaveButton { note ->
                    val h = result.height
                    save(if (h != null) tool.record(h, "m", quality, "clinometer_height", details = "d ${formatLength(result.distance)}", note = note)
                        else tool.record(result.distance, "m", quality, "clinometer_distance", note = note))
                }
            }
            Text(stringResource(R.string.angle_level_ground), style = MaterialTheme.typography.bodySmall)
        }

        if (editHeight) NumberInputDialog(stringResource(R.string.angle_eye_height), stringResource(R.string.angle_eye_hint), "m",
            eyeHeight.toString(), { editHeight = false }) { v -> if (v in 0.1..20.0) eyeHeight = v }
        if (editDistance) NumberInputDialog(stringResource(R.string.angle_known_distance), null, "m",
            knownDistance?.toString() ?: "", { editDistance = false }) { v -> if (v > 0) { knownDistance = v; baseAngle = null } }
    }
}

@Composable
private fun Crosshair(modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val s = size.minDimension * 0.08f
        drawLine(Color.White, Offset(c.x - s, c.y), Offset(c.x + s, c.y), 3f)
        drawLine(Color.White, Offset(c.x, c.y - s), Offset(c.x, c.y + s), 3f)
        drawCircle(Color.White, s * 0.6f, c, style = Stroke(2f))
        drawLine(Color(0x88FFFFFF), Offset(0f, c.y), Offset(size.width, c.y), 1f)
    }
}
