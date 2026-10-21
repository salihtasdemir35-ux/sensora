package com.sensora.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.sensora.app.R
import com.sensora.app.core.math.UnitCategory
import com.sensora.app.core.math.UnitConverter
import com.sensora.app.core.math.UnitDef
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun unitCategoryLabel(c: UnitCategory): Int = when (c) {
    UnitCategory.LENGTH -> R.string.unitcat_length
    UnitCategory.AREA -> R.string.unitcat_area
    UnitCategory.VOLUME -> R.string.unitcat_volume
    UnitCategory.MASS -> R.string.unitcat_mass
    UnitCategory.SPEED -> R.string.unitcat_speed
    UnitCategory.TEMPERATURE -> R.string.unitcat_temperature
    UnitCategory.PRESSURE -> R.string.unitcat_pressure
    UnitCategory.ENERGY -> R.string.unitcat_energy
    UnitCategory.POWER -> R.string.unitcat_power
}

fun formatSmart(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val a = abs(v)
    return if (a != 0.0 && (a >= 1e9 || a < 1e-4)) String.format(Locale.getDefault(), "%.4e", v)
    else NumberFormat.getNumberInstance().apply { maximumFractionDigits = 6 }.format(v)
}

@Composable
fun ConverterScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        var cat by rememberSaveable { mutableStateOf(0) }
        val category = UnitCategory.entries[cat]
        val units = UnitConverter.units(category)
        var from by rememberSaveable(cat) { mutableStateOf(0) }
        var to by rememberSaveable(cat) { mutableStateOf(1) }
        var input by rememberSaveable { mutableStateOf("1") }
        val value = input.replace(',', '.').toDoubleOrNull()

        ScreenColumn(padding) {
            ChoiceChips(UnitCategory.entries.map { stringResource(unitCategoryLabel(it)) }, cat) { cat = it }
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text(stringResource(R.string.conv_value)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnitPicker(units, from, { from = it }, Modifier.weight(1f))
                IconButton(onClick = { val t = from; from = to; to = t }) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.conv_swap))
                }
                UnitPicker(units, to, { to = it }, Modifier.weight(1f))
            }
            val result = value?.let { UnitConverter.convert(it, category, units[from].id, units[to].id) }
            BigValue(result?.let { formatSmart(it) } ?: "—", units[to].symbol)
            if (value != null) {
                InfoCard {
                    Text(stringResource(R.string.conv_all), style = MaterialTheme.typography.titleSmall)
                    units.forEach { u ->
                        InfoRow(u.symbol, formatSmart(UnitConverter.convert(value, category, units[from].id, u.id)))
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(units: List<UnitDef>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(units[selected.coerceIn(0, units.lastIndex)].symbol)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            units.forEachIndexed { i, u ->
                DropdownMenuItem(text = { Text(u.symbol) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}
