package com.sensora.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.ui.common.fmt

/** 0 = degrees, 1 = percent slope, 2 = mm per metre */
fun formatSlope(deg: Double, unit: Int): String = when (unit) {
    0 -> "${fmt(deg, 1)}°"
    1 -> if (kotlin.math.abs(deg) < 89.0) "${fmt(OrientationMath.slopePercent(deg), 1)} %" else "—"
    else -> if (kotlin.math.abs(deg) < 89.0) "${fmt(OrientationMath.slopeMmPerM(deg), 1)} mm/m" else "—"
}

@Composable
fun CenterRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun MinAvgMax(minLabel: String, avgLabel: String, maxLabel: String, min: String, avg: String, max: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(minLabel to min, avgLabel to avg, maxLabel to max).forEach { (l, v) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l)
                Text(v)
            }
        }
    }
}
