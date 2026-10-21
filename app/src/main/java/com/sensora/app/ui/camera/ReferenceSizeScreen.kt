package com.sensora.app.ui.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.camera.ReferenceScale
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlin.math.hypot

private val REF_A = Color(0xFF42A5F5)
private val REF_T = Color(0xFFFFB300)

@Composable
fun ReferenceSizeScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            PermissionGate(CAMERA_PERMISSION, R.string.camera_rationale) { ReferenceContent(tool) }
        }
    }
}

@Composable
private fun ReferenceContent(tool: Tool) {
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    // Handles as fractions of the image: reference A, reference B, target A, target B.
    val handles = remember { mutableStateListOf(Offset(0.25f, 0.4f), Offset(0.6f, 0.4f), Offset(0.25f, 0.6f), Offset(0.75f, 0.6f)) }
    var preset by rememberSaveable { mutableStateOf(0) }
    var customMm by rememberSaveable { mutableStateOf(100.0) }
    var editCustom by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val save = rememberMeasurementSaver()
    val presetsMm = listOf(85.6, 297.0, 210.0)

    Text(stringResource(R.string.ref_steps), style = MaterialTheme.typography.bodyMedium)
    val bmp = photo
    if (bmp == null) {
        CameraPreview(Modifier.fillMaxWidth().aspectRatio(3f / 4f), onReady = { _, v -> preview = v })
        Button(onClick = { photo = preview?.bitmap }, modifier = Modifier.fillMaxWidth(), enabled = preview != null) {
            Text(stringResource(R.string.ref_freeze))
        }
        return
    }

    Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).onSizeChanged { boxSize = it }) {
        Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        var active by remember { mutableStateOf(-1) }
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    active = handles.indices.minByOrNull { i -> hypot(handles[i].x * w - o.x, handles[i].y * h - o.y) }
                        ?.takeIf { i -> hypot(handles[i].x * w - o.x, handles[i].y * h - o.y) < 48 * density } ?: -1
                },
                onDragEnd = { active = -1 },
                onDrag = { change, drag ->
                    if (active >= 0) {
                        change.consume()
                        val p = handles[active]
                        handles[active] = Offset((p.x + drag.x / size.width).coerceIn(0f, 1f), (p.y + drag.y / size.height).coerceIn(0f, 1f))
                    }
                },
            )
        }) {
            fun pt(i: Int) = Offset(handles[i].x * size.width, handles[i].y * size.height)
            drawLine(REF_A, pt(0), pt(1), 4f)
            drawLine(REF_T, pt(2), pt(3), 4f)
            for (i in 0..3) {
                val color = if (i < 2) REF_A else REF_T
                drawCircle(color.copy(alpha = 0.35f), 18f * density, pt(i))
                drawCircle(color, 18f * density, pt(i), style = Stroke(2f * density))
            }
        }
    }

    ChoiceChips(listOf(stringResource(R.string.ref_card), stringResource(R.string.ref_a4_long), stringResource(R.string.ref_a4_short),
        stringResource(R.string.ref_custom)), preset) { preset = it; if (it == 3) editCustom = true }
    val refMm = if (preset < 3) presetsMm[preset] else customMm
    // Pixel lengths in the original photo's resolution.
    val scale = if (boxSize.width > 0) bmp.width.toDouble() / boxSize.width else 1.0
    fun len(a: Int, b: Int) = hypot(((handles[a].x - handles[b].x) * boxSize.width).toDouble(), ((handles[a].y - handles[b].y) * boxSize.height).toDouble()) * scale
    val refPx = len(0, 1); val targetPx = len(2, 3)
    val result = ReferenceScale.length(refMm, refPx, targetPx)
    val quality = ReferenceScale.quality(refPx, targetPx)
    InfoCard {
        InfoRow(stringResource(R.string.ref_reference), "${fmt(refMm, 1)} mm")
        InfoRow(stringResource(R.string.ref_result), result?.let { if (it >= 1000) "${fmt(it / 1000, 3)} m" else "${fmt(it / 10, 1)} cm" } ?: "—")
        QualityBadge(quality)
        Text(stringResource(R.string.ref_rules), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { photo = null }) { Text(stringResource(R.string.ref_retake)) }
    }
    if (result != null) SaveButton { note -> save(tool.record(result / 1000.0, "m", quality, "reference_object", details = "ref ${fmt(refMm, 1)} mm", note = note)) }

    if (editCustom) NumberInputDialog(stringResource(R.string.ref_custom), null, "mm", customMm.toString(), { editCustom = false }) { v -> if (v > 0) customMm = v }
}

