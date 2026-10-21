package com.sensora.app.ui.health

import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.health.PulseAnalyzer
import com.sensora.app.core.health.PulseResult
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.camera.CAMERA_PERMISSION
import com.sensora.app.ui.camera.CameraPreview
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private const val MEASURE_SEC = 30.0

/** Collects fingertip brightness from camera frames (analysis thread) for the UI thread to analyse. */
private class PpgCollector {
    private val lock = Any()
    private val t = ArrayList<Double>()
    private val v = ArrayList<Double>()
    private var startNs = -1L
    @Volatile var finger = false

    fun add(timestampNs: Long, brightness: Double) = synchronized(lock) {
        if (startNs < 0) startNs = timestampNs
        t.add((timestampNs - startNs) / 1e9); v.add(brightness)
    }

    fun reset() = synchronized(lock) { t.clear(); v.clear(); startNs = -1L }

    fun snapshot(): Pair<DoubleArray, DoubleArray> = synchronized(lock) { t.toDoubleArray() to v.toDoubleArray() }

    fun duration(): Double = synchronized(lock) { if (t.isEmpty()) 0.0 else t.last() }
}

/** Mean of the central region of a plane, sampling every 4th pixel. */
private fun planeMean(image: ImageProxy, index: Int): Double {
    val plane = image.planes[index]
    val buf = plane.buffer
    val div = if (index == 0) 1 else 2
    val w = image.width / div; val h = image.height / div
    var sum = 0L; var n = 0
    var y = h / 4
    while (y < 3 * h / 4) {
        var x = w / 4
        while (x < 3 * w / 4) {
            val i = y * plane.rowStride + x * plane.pixelStride
            if (i < buf.limit()) { sum += (buf.get(i).toInt() and 0xFF); n++ }
            x += 4
        }
        y += 4
    }
    return if (n == 0) 0.0 else sum.toDouble() / n
}

@Composable
fun PulseScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.pulse_disclaimer))
            PermissionGate(CAMERA_PERMISSION, R.string.camera_rationale) { PulseContent(tool) }
        }
    }
}

@Composable
private fun PulseContent(tool: Tool) {
    val save = rememberMeasurementSaver()
    val collector = remember { PpgCollector() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    var measuring by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var finger by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(listOf<Float>()) }
    var result by remember { mutableStateOf<PulseResult?>(null) }

    val analysis = remember {
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a ->
            a.setAnalyzer(executor) { image ->
                try {
                    val y = planeMean(image, 0)
                    val cr = planeMean(image, 2)
                    // A fingertip lit by the flash looks bright and strongly red (high Cr).
                    val ok = cr > 150 && y in 25.0..245.0
                    collector.finger = ok
                    if (ok) collector.add(image.imageInfo.timestamp, y) else collector.reset()
                } finally {
                    image.close()
                }
            }
        }
    }

    LaunchedEffect(measuring, camera) { camera?.cameraControl?.enableTorch(measuring) }
    LaunchedEffect(measuring) {
        if (!measuring) return@LaunchedEffect
        collector.reset(); result = null
        while (measuring) {
            delay(250)
            finger = collector.finger
            val d = collector.duration()
            progress = (d / MEASURE_SEC).toFloat().coerceIn(0f, 1f)
            val (t, v) = collector.snapshot()
            val tail = v.takeLast(150)
            if (tail.size > 10) { val m = tail.average(); wave = tail.map { (m - it).toFloat() } }
            if (d >= MEASURE_SEC) {
                result = PulseAnalyzer.analyze(t, v)
                measuring = false
            }
        }
    }

    InfoCard {
        Text(stringResource(R.string.pulse_steps), style = MaterialTheme.typography.bodyMedium)
    }
    if (measuring) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraPreview(Modifier.size(72.dp).clip(CircleShape), analysis = analysis, onReady = { c, _ -> camera = c })
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(if (finger) R.string.pulse_finger_ok else R.string.pulse_no_finger),
                    color = if (finger) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        LineGraph(wave)
        TextButton(onClick = { measuring = false }) { Text(stringResource(R.string.cancel)) }
    } else {
        Button(onClick = { measuring = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pulse_start)) }
    }

    result?.let { r ->
        if (r.bpm == null) {
            WarningCard(stringResource(R.string.pulse_unreliable), isError = true)
        } else {
            ResultCard(stringResource(R.string.tool_pulse)) {
                BigValue(fmt(r.bpm, 0), stringResource(R.string.pulse_bpm))
                QualityBadge(r.quality)
                r.rmssdMs?.let { InfoRow(stringResource(R.string.pulse_hrv), "${fmt(it, 0)} ms") }
                Note(if (PulseAnalyzer.urgency(r.bpm) == com.sensora.app.core.health.Urgency.NORMAL) R.string.pulse_normal else R.string.pulse_abnormal)
                UrgencyBanner(PulseAnalyzer.urgency(r.bpm))
                Note(R.string.pulse_hrv_note)
                SaveButton { note -> save(tool.record(r.bpm, "bpm", r.quality, "camera_ppg",
                    details = r.rmssdMs?.let { "RMSSD ${fmt(it, 0)} ms" }, note = note)) }
            }
        }
    }
    Note(R.string.pulse_blood_note)
}
