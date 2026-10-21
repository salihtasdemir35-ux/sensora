@Composable
private fun Note(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
package com.sensora.app.ui.quake
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sensora.app.R
import com.sensora.app.core.model.Quality
import com.sensora.app.core.quake.Earthquake
import com.sensora.app.core.quake.Fft
import com.sensora.app.core.quake.Intensity
import com.sensora.app.core.quake.QuakeParsers
import com.sensora.app.core.quake.QuakeSource
import com.sensora.app.core.quake.VibrationBuffer
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.astro.formatDate
import com.sensora.app.ui.astro.formatTime
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import com.sensora.app.ui.theme.BadColor
import com.sensora.app.ui.theme.GoodColor
import com.sensora.app.ui.theme.LimitedColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun QuakeScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        var tab by rememberSaveable { mutableStateOf(0) }
        ScreenColumn(padding) {
            ChoiceChips(listOf(stringResource(R.string.quake_tab_seismo), stringResource(R.string.quake_tab_list),
                stringResource(R.string.quake_tab_safety)), tab) { tab = it }
            when (tab) {
                0 -> Seismograph(tool)
                1 -> RecentQuakes()
                else -> Safety()
            }
        }
    }
}

// ------------------------------------------------------------ Seismograph & vibration analysis
@Composable
private fun Seismograph(tool: Tool) {
    val sm = rememberSensorManager()
    val owner = LocalLifecycleOwner.current
    val linear = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val accel = remember(sm) { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val sensor = linear ?: accel
    if (sensor == null) { UnavailableCard(stringResource(R.string.accel_unavailable)); return }
    val buffer = remember { VibrationBuffer(4096) }
    var tick by remember { mutableStateOf(0) }
    var axis by rememberSaveable { mutableStateOf(3) }
    val save = rememberMeasurementSaver()

    LaunchedEffect(sensor) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val g = FloatArray(3)
            var init = false
            SensorReaders.samples(sm, sensor.type, SensorManager.SENSOR_DELAY_FASTEST).collect { s ->
                if (linear != null) buffer.add(s.timestampNs, s.values[0], s.values[1], s.values[2])
                else {
                    // High-pass: remove gravity from the raw accelerometer.
                    if (!init) { s.values.copyInto(g, 0, 0, 3); init = true }
                    for (i in 0..2) g[i] = 0.98f * g[i] + 0.02f * s.values[i]
                    buffer.add(s.timestampNs, s.values[0] - g[0], s.values[1] - g[1], s.values[2] - g[2])
                }
            }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(120); tick++ } }

    @Suppress("UNUSED_VARIABLE") val redraw = tick
    val fs = buffer.sampleRate()
    val peak = buffer.peak
    val rms = buffer.rms(fs.toInt().coerceAtLeast(10))
    val mmi = Intensity.mmiFromPga(peak)

    WarningCard(stringResource(R.string.quake_seismo_disclaimer))
    Card(Modifier.fillMaxWidth()) {
        val colors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
            val n = 600
            val series = (0..2).map { buffer.last(n, it) }
            val amp = maxOf(0.05, series.maxOf { s -> s.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0 })
            drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
            series.forEachIndexed { k, s ->
                if (s.size < 2) return@forEachIndexed
                val p = Path()
                val step = maxOf(1, s.size / 300)
                var first = true
                for (i in s.indices step step) {
                    val x = i * size.width / (s.size - 1)
                    val y = (size.height / 2 - s[i] / amp * size.height / 2.2).toFloat()
                    if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
                }
                drawPath(p, colors[k], style = Stroke(2f))
            }
        }
        Text("X  Y  Z · ±${fmt(0.0 + maxOf(0.05, peak), 2)} m/s²", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
    }
    InfoCard {
        InfoRow(stringResource(R.string.quake_peak), "${fmt(peak, 3)} m/s² · ${fmt(peak / 9.80665, 4)} g")
        InfoRow(stringResource(R.string.quake_rms), "${fmt(rms, 3)} m/s²")
        InfoRow(stringResource(R.string.quake_intensity), Intensity.roman(mmi))
        Note(R.string.quake_intensity_note)
        TextButton(onClick = { buffer.resetPeak() }) { Text(stringResource(R.string.reset)) }
    }

    Text(stringResource(R.string.quake_fft_title), style = MaterialTheme.typography.titleMedium)
    ChoiceChips(listOf("X", "Y", "Z", "|a|"), axis) { axis = it }
    if (buffer.count >= 512 && fs > 10) {
        val spec = Fft.amplitudeSpectrum(buffer.last(512, axis))
        val dom = Fft.dominant(spec, fs, 512)
        val barColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val top = spec.drop(1).maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0
            val bw = size.width / (spec.size - 1)
            for (k in 1 until spec.size) {
                val h = (spec[k] / top * size.height).toFloat()
                drawRect(barColor, Offset((k - 1) * bw, size.height - h), Size(bw.coerceAtLeast(1f), h))
            }
        }
        Text("0 Hz — ${fmt(fs / 2, 0)} Hz", style = MaterialTheme.typography.labelSmall)
        InfoCard {
            InfoRow(stringResource(R.string.quake_dominant), "${fmt(dom.frequencyHz, 1)} Hz · ${fmt(dom.amplitude, 3)} m/s²")
            InfoRow(stringResource(R.string.quake_sample_rate), "${fmt(fs, 0)} Hz")
            InfoRow(stringResource(R.string.quake_sensor), "${sensor.name} · ±${fmt(sensor.maximumRange.toDouble(), 1)} m/s² · ${fmt(sensor.resolution.toDouble(), 4)}")
            Note(R.string.quake_fft_note)
        }
        SaveButton { note -> save(tool.record(peak, "m/s²", Quality.LIMITED, "accelerometer_pga",
            details = "MMI~${Intensity.roman(mmi)} · f=${fmt(dom.frequencyHz, 1)} Hz · fs=${fmt(fs, 0)} Hz", note = note)) }
    } else WaitingCard()
}

// ------------------------------------------------------------ Official earthquake lists
private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000; c.readTimeout = 15_000
    c.setRequestProperty("Accept", "application/json")
    try {
        check(c.responseCode in 200..299) { "HTTP ${c.responseCode}" }
        c.inputStream.bufferedReader().use { it.readText() }
    } finally { c.disconnect() }
}

private fun magColor(m: Double) = when { m >= 5.0 -> BadColor; m >= 4.0 -> LimitedColor; else -> GoodColor }

@Composable
private fun RecentQuakes() {
    val ctx = LocalContext.current
    val observer by ctx.sensoraApp.prefs.observer.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf(0) }
    var period by rememberSaveable { mutableStateOf(0) }
    var minMagIndex by rememberSaveable { mutableStateOf(1) }
    var reload by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<Earthquake>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var fetchedAt by remember { mutableStateOf<Long?>(null) }
    val mags = listOf(2.0, 3.0, 4.0, 5.0)

    LaunchedEffect(source, period, minMagIndex, reload) {
        loading = true; error = false
        val hours = if (period == 0) 24 else 24 * 7
        val minMag = mags[minMagIndex]
        try {
            val list = if (source == 0) QuakeParsers.afad(download(QuakeParsers.afadUrl(System.currentTimeMillis(), hours, minMag)))
            else QuakeParsers.usgs(download(QuakeParsers.usgsUrl(hours, minMag)))
            val since = System.currentTimeMillis() - hours * 3_600_000L
            items = list.filter { it.magnitude >= minMag && it.timeMs >= since }.sortedByDescending { it.timeMs }
            fetchedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            error = true
        }
        loading = false
    }

    ChoiceChips(listOf(stringResource(R.string.quake_src_afad), stringResource(R.string.quake_src_usgs)), source) { source = it }
    ChoiceChips(listOf(stringResource(R.string.quake_24h), stringResource(R.string.quake_7d)), period) { period = it }
    ChoiceChips(mags.map { "M ≥ ${fmt(it, 0)}" }, minMagIndex) { minMagIndex = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fetchedAt?.let { stringResource(R.string.quake_updated, formatTime(it)) } ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { reload++ }, enabled = !loading) { Text(stringResource(R.string.quake_refresh)) }
    }
    if (loading) WaitingCard()
    if (error) WarningCard(stringResource(R.string.quake_error), isError = true)
    if (observer == null) Note(R.string.quake_distance_hint)
    val list = items
    if (!loading && list != null && list.isEmpty()) Text(stringResource(R.string.quake_none))
    list?.take(200)?.forEach { q ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = magColor(q.magnitude), shape = RoundedCornerShape(10.dp)) {
                    Text(fmt(q.magnitude, 1), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.place.ifBlank { "${fmt(q.lat, 2)}, ${fmt(q.lon, 2)}" }, fontWeight = FontWeight.SemiBold)
                    Text("${formatDate(q.timeMs)} ${formatTime(q.timeMs)} · ${stringResource(R.string.quake_depth, fmt(q.depthKm, 1))}",
                        style = MaterialTheme.typography.bodySmall)
                    observer?.let { o -> Text(stringResource(R.string.quake_distance, fmt(q.distanceKm(o.latDeg, o.lonDeg), 0)), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    Note(if (source == 0) R.string.quake_afad_note else R.string.quake_usgs_note)
}

// ------------------------------------------------------------ Safety guidance
@Composable
private fun Safety() {
    InfoCard {
        Text(stringResource(R.string.quake_during_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.quake_during), style = MaterialTheme.typography.bodyMedium)
    }
    InfoCard {
        Text(stringResource(R.string.quake_before_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.quake_before), style = MaterialTheme.typography.bodyMedium)
    }
    InfoCard {
        Text(stringResource(R.string.quake_after_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.quake_after), style = MaterialTheme.typography.bodyMedium)
    }
    WarningCard(stringResource(R.string.quake_alerts_note))
}
@Composable
private fun Note(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
