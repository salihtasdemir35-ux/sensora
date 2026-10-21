package com.sensora.app.ui.tools

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.sensor.CompassMethod
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.compass_unavailable)) }
            return@ToolScaffold
        }
        val ctx = LocalContext.current
        val reading by remember(sm) { SensorReaders.compass(sm) }.collectAsStateWithLifecycle(initialValue = null)
        var declination by rememberSaveable { mutableStateOf<Float?>(null) }
        var wantTrueNorth by rememberSaveable { mutableStateOf(false) }
        val points = stringArrayResource(R.array.compass_points)
        val save = rememberMeasurementSaver()

        ScreenColumn(padding) {
            val r = reading
            if (r == null) {
                WaitingCard()
                return@ScreenColumn
            }
            val trueHeading = declination?.let { OrientationMath.normalizeDegrees(r.heading + it) }
            val shown = trueHeading ?: r.heading
            val quality = QualityRules.compass(r.accuracy, r.fieldMicroTesla)
            val cardinal = points[OrientationMath.cardinal16(shown)]

            CompassDial(shown.toFloat(), points, Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally))
            BigValue("${shown.roundToInt() % 360}°", cardinal,
                caption = stringResource(if (trueHeading != null) R.string.compass_true else R.string.compass_magnetic))
            CenterRow { QualityBadge(quality) }

            InfoCard {
                InfoRow(stringResource(R.string.compass_magnetic), "${fmt(r.heading, 0)}°")
                if (trueHeading != null) InfoRow(stringResource(R.string.compass_true), "${fmt(trueHeading, 0)}°")
                declination?.let {
                    InfoRow(stringResource(R.string.compass_declination), String.format(Locale.getDefault(), "%+.1f°", it))
                }
                InfoRow(stringResource(R.string.compass_field), "${fmt(r.fieldMicroTesla, 0)} µT")
                InfoRow(stringResource(R.string.method), stringResource(
                    if (r.method == CompassMethod.ROTATION_VECTOR) R.string.method_fusion else R.string.method_accel_mag))
            }
            if (QualityRules.fieldInterference(r.fieldMicroTesla)) WarningCard(stringResource(R.string.compass_interference))
            if (r.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_HIGH) FigureEightHint(stringResource(R.string.compass_calibrate_hint))

            if (declination == null) {
                InfoCard {
                    Text(stringResource(R.string.compass_true_needs_location), style = MaterialTheme.typography.bodyMedium)
                    if (!wantTrueNorth) {
                        OutlinedButton(onClick = { wantTrueNorth = true }) { Text(stringResource(R.string.compass_use_location)) }
                    } else {
                        PermissionGate(LOCATION_PERMISSIONS, R.string.location_rationale) {
                            LaunchedEffect(Unit) {
                                val loc = SensorReaders.currentLocation(ctx)
                                if (loc != null) {
                                    declination = GeomagneticField(
                                        loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(),
                                        System.currentTimeMillis(),
                                    ).declination
                                }
                            }
                            Text(stringResource(R.string.gps_waiting))
                        }
                    }
                }
            }

            SaveButton { note ->
                save(tool.record(shown, "°", quality, r.method.name,
                    details = "$cardinal · " + (if (trueHeading != null) "true" else "magnetic"), note = note))
            }
        }
    }
}

@Composable
private fun CompassDial(heading: Float, points: Array<String>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.tertiary
    val north = Color(0xFFE53935)
    val labelStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    Canvas(modifier.aspectRatio(1f).padding(8.dp)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(primary.copy(alpha = 0.08f), r)
        drawCircle(primary, r, style = Stroke(4f))
        rotate(-heading, c) {
            for (d in 0 until 360 step 5) {
                val major = d % 30 == 0
                val len = if (major) r * 0.12f else if (d % 10 == 0) r * 0.07f else r * 0.04f
                val a = Math.toRadians(d - 90.0)
                val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                drawLine(
                    if (d == 0) north else onSurface,
                    Offset(c.x + (r * 0.95f - len) * ca, c.y + (r * 0.95f - len) * sa),
                    Offset(c.x + r * 0.95f * ca, c.y + r * 0.95f * sa),
                    strokeWidth = if (major) 4f else 2f,
                )
            }
            listOf(0 to points[0], 90 to points[4], 180 to points[8], 270 to points[12]).forEach { (d, label) ->
                val a = Math.toRadians(d - 90.0)
                val pos = Offset(c.x + r * 0.70f * cos(a).toFloat(), c.y + r * 0.70f * sin(a).toFloat())
                val layout = measurer.measure(label, labelStyle.copy(color = if (d == 0) north else onSurface))
                rotate(heading, pos) {
                    drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                }
            }
        }
        val lubber = Path().apply {
            moveTo(c.x, c.y - r * 0.80f)
            lineTo(c.x - r * 0.06f, c.y - r * 0.98f)
            lineTo(c.x + r * 0.06f, c.y - r * 0.98f)
            close()
        }
        drawPath(lubber, accent)
        drawCircle(accent, r * 0.03f, c)
    }
}
