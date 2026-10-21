package com.sensora.app.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.sensora.app.R
import com.sensora.app.core.camera.Geometry
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*

private data class Shape(val label: Int, val fields: List<Int>, val isVolume: Boolean, val compute: (List<Double>) -> Double?)

private val SHAPES = listOf(
    Shape(R.string.shape_rectangle, listOf(R.string.dim_length, R.string.dim_width), false) { Geometry.rectangle(it[0], it[1]) },
    Shape(R.string.shape_circle, listOf(R.string.dim_radius), false) { Geometry.circle(it[0]) },
    Shape(R.string.shape_triangle, listOf(R.string.dim_side_a, R.string.dim_side_b, R.string.dim_side_c), false) { Geometry.triangle(it[0], it[1], it[2]) },
    Shape(R.string.shape_wall, listOf(R.string.dim_width, R.string.dim_height, R.string.dim_openings), false) { Geometry.wall(it[0], it[1], it[2]) },
    Shape(R.string.shape_box, listOf(R.string.dim_length, R.string.dim_width, R.string.dim_height), true) { Geometry.box(it[0], it[1], it[2]) },
    Shape(R.string.shape_cylinder, listOf(R.string.dim_radius, R.string.dim_height), true) { Geometry.cylinder(it[0], it[1]) },
    Shape(R.string.shape_sphere, listOf(R.string.dim_radius), true) { Geometry.sphere(it[0]) },
    Shape(R.string.shape_cone, listOf(R.string.dim_radius, R.string.dim_height), true) { Geometry.cone(it[0], it[1]) },
)

private val UNITS = listOf("m" to 1.0, "cm" to 0.01, "mm" to 0.001, "ft" to 0.3048, "in" to 0.0254)

@Composable
fun AreaVolumeScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        var shapeIndex by rememberSaveable { mutableStateOf(0) }
        var unitIndex by rememberSaveable { mutableStateOf(0) }
        val inputs = remember { mutableStateListOf("", "", "") }
        val save = rememberMeasurementSaver()
        val shape = SHAPES[shapeIndex]
        val factor = UNITS[unitIndex].second

        ScreenColumn(padding) {
            ChoiceChips(SHAPES.map { stringResource(it.label) }, shapeIndex) { shapeIndex = it; for (i in 0..2) inputs[i] = "" }
            ChoiceChips(UNITS.map { it.first }, unitIndex) { unitIndex = it }
            shape.fields.forEachIndexed { i, label ->
                OutlinedTextField(
                    value = inputs[i], onValueChange = { inputs[i] = it }, singleLine = true,
                    label = { Text("${stringResource(label)} (${UNITS[unitIndex].first}${if (label == R.string.dim_openings) "²" else ""})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val values = shape.fields.indices.map { i ->
                val v = inputs[i].replace(',', '.').toDoubleOrNull()
                // Openings are an area, so they scale with the square of the unit.
                v?.let { if (shape.fields[i] == R.string.dim_openings) it * factor * factor else it * factor }
            }
            val ready = values.all { it != null && it >= 0 }
            val result = if (ready) shape.compute(values.map { it!! }) else null
            if (ready && result == null) WarningCard(stringResource(R.string.shape_invalid))
            if (result != null) {
                InfoCard {
                    if (shape.isVolume) {
                        InfoRow(stringResource(R.string.result_volume), "${fmt(result, 3)} m³")
                        InfoRow("", "${fmt(result * 1000, 1)} L")
                        InfoRow("", "${fmt(result / 0.028316846592, 2)} ft³")
                    } else {
                        InfoRow(stringResource(R.string.result_area), "${fmt(result, 3)} m²")
                        InfoRow("", "${fmt(result / 0.09290304, 2)} ft²")
                    }
                }
                SaveButton { note ->
                    save(tool.record(result, if (shape.isVolume) "m³" else "m²", Quality.GOOD, "manual_dimensions",
                        details = values.joinToString(" × ") { fmt(it!!, 3) }, note = note))
                }
            }
            Text(stringResource(R.string.areavol_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
