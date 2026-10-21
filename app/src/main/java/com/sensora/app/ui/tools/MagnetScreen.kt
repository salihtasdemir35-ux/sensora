package com.sensora.app.ui.tools

import android.hardware.Sensor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.QualityRules
import com.sensora.app.core.model.Quality
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlin.math.abs

@Composable
fun MagnetScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val sm = rememberSensorManager()
        if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
            ScreenColumn(padding) { UnavailableCard(stringResource(R.string.compass_unavailable)) }
            return@ToolScaffold
        }
        val sample by remember(sm) { SensorReaders.samples(sm, Sensor.TYPE_MAGNETIC_FIELD) }
            .collectAsStateWithLifecycle(initialValue = null)
        val history = remember { mutableStateListOf<Float>() }
        var baseline by remember { mutableStateOf<Double?>(null) }
        val save = rememberMeasurementSaver()

        LaunchedEffect(sample) {
            val v = sample?.values ?: return@LaunchedEffect
            history.add(OrientationMath.magnitude(v[0].toDouble(), v[1].toDouble(), v[2].toDouble()).toFloat())
            if (history.size > 200) history.removeAt(0)
        }

        ScreenColumn(padding) {
            val s = sample
            if (s == null) {
                WaitingCard()
                return@ScreenColumn
            }
            val total = OrientationMath.magnitude(s.values[0].toDouble(), s.values[1].toDouble(), s.values[2].toDouble())
            val quality = Quality.worst(QualityRules.fromSensorAccuracy(s.accuracy), Quality.GOOD)
            val delta = baseline?.let { total - it }

            BigValue(fmt(total, 1), "µT", caption = stringResource(R.string.magnet_total))
            CenterRow { QualityBadge(quality) }
            if (delta != null && abs(delta) > 50) WarningCard(stringResource(R.string.magnet_strong))
            InfoCard {
                Text(stringResource(R.string.magnet_axes), style = MaterialTheme.typography.titleSmall)
                InfoRow("X", "${fmt(s.values[0].toDouble(), 1)} µT")
                InfoRow("Y", "${fmt(s.values[1].toDouble(), 1)} µT")
                InfoRow("Z", "${fmt(s.values[2].toDouble(), 1)} µT")
                if (delta != null) InfoRow(stringResource(R.string.magnet_delta), String.format(java.util.Locale.getDefault(), "%+.1f µT", delta))
            }
            LineGraph(history.toList(), minY = 0f)
            CenterRow {
                OutlinedButton(onClick = { baseline = total }) { Text(stringResource(R.string.magnet_baseline)) }
                if (baseline != null) TextButton(onClick = { baseline = null }) { Text(stringResource(R.string.reset)) }
            }
            if (s.accuracy < 3) FigureEightHint(stringResource(R.string.compass_calibrate_hint))
            Text(stringResource(R.string.magnet_note), style = MaterialTheme.typography.bodySmall)
            SaveButton { note ->
                save(tool.record(total, "µT", quality, "magnetometer",
                    details = delta?.let { "Δ ${fmt(it, 1)} µT" }, note = note))
            }
        }
    }
}
