package com.sensora.app.ui.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.sensora.app.R
import com.sensora.app.core.camera.ArQuality
import com.sensora.app.core.camera.HitKind
import com.sensora.app.core.camera.MeasureMath
import com.sensora.app.core.model.Quality
import com.sensora.app.ui.camera.CAMERA_PERMISSION
import com.sensora.app.ui.camera.formatLength
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun ArMeasureScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val ctx = LocalContext.current
        var availability by remember { mutableStateOf(ArCoreApk.getInstance().checkAvailability(ctx)) }
        LaunchedEffect(availability) {
            if (availability.isTransient) { delay(300); availability = ArCoreApk.getInstance().checkAvailability(ctx) }
        }
        LifecycleResumeEffect(Unit) {
            availability = ArCoreApk.getInstance().checkAvailability(ctx)
            onPauseOrDispose { }
        }
        when {
            availability.isTransient -> ScreenColumn(padding) { WaitingCard() }
            !availability.isSupported -> ScreenColumn(padding) {
                UnavailableCard(stringResource(R.string.ar_unsupported))
                Text(stringResource(R.string.ar_alternatives), style = MaterialTheme.typography.bodyMedium)
            }
            availability != ArCoreApk.Availability.SUPPORTED_INSTALLED -> ScreenColumn(padding) {
                var failed by remember { mutableStateOf(false) }
                InfoCard {
                    Text(stringResource(R.string.ar_install_needed))
                    Button(onClick = {
                        val activity = ctx.findActivity()
                        failed = activity == null || runCatching { ArCoreApk.getInstance().requestInstall(activity, true) }.isFailure
                    }) { Text(stringResource(R.string.ar_install)) }
                    if (failed) Text(stringResource(R.string.ar_install_failed), color = MaterialTheme.colorScheme.error)
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) {
                PermissionGate(CAMERA_PERMISSION, R.string.camera_rationale) { ArSession(tool) }
            }
        }
    }
}

@Composable
private fun ArSession(tool: Tool) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { ArController(ctx.applicationContext) }
    var error by remember { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 length, 1 area
    val glView = remember {
        GLSurfaceView(ctx).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(controller)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    DisposableEffect(owner) {
        try {
            if (controller.session == null) {
                val s = Session(ctx)
                val config = Config(s).apply {
                    if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) depthMode = Config.DepthMode.AUTOMATIC
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                s.configure(config)
                controller.session = s
            }
        } catch (e: Exception) {
            error = e.javaClass.simpleName
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> try {
                    controller.session?.resume(); glView.onResume()
                } catch (e: Exception) { error = e.javaClass.simpleName }
                Lifecycle.Event.ON_PAUSE -> { glView.onPause(); controller.session?.pause() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            glView.onPause()
            controller.session?.let { s -> s.pause(); controller.releaseAnchors(); s.close() }
            controller.session = null
        }
    }

    val st by controller.state.collectAsStateWithLifecycle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val save = rememberMeasurementSaver()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val pts = st.points
            val lineColor = Color(0xFFFFD54F)
            val label = TextStyle(color = Color.White, fontSize = 13.sp, background = Color(0x99000000))
            val n = pts.size
            val segments = if (mode == 1 && n >= 3) n else n - 1
            for (i in 0 until segments.coerceAtLeast(0)) {
                val a = pts[i]; val b = pts[(i + 1) % n]
                if (a.onScreen && b.onScreen) {
                    drawLine(lineColor, Offset(a.screenX, a.screenY), Offset(b.screenX, b.screenY), 4f * density)
                    val layout = measurer.measure(formatLength(MeasureMath.distance(a.world, b.world)), label)
                    drawText(layout, topLeft = Offset((a.screenX + b.screenX) / 2 - layout.size.width / 2, (a.screenY + b.screenY) / 2 - layout.size.height - 4))
                }
            }
            pts.forEach { p -> if (p.onScreen) drawCircle(Color.White, 7f * density, Offset(p.screenX, p.screenY)) }
            val ok = st.centerKind != null
            drawCircle(if (ok) Color.White else Color(0x88FFFFFF), 16f * density, center, style = Stroke(2.5f * density))
            drawCircle(if (ok) Color.White else Color(0x88FFFFFF), 2.5f * density, center)
        }

        Column(Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val hint = when {
                error != null -> stringResource(R.string.ar_error, error!!)
                !st.tracking -> stringResource(when (st.failure) {
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.ar_fail_light
                    TrackingFailureReason.EXCESSIVE_MOTION -> R.string.ar_fail_motion
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> R.string.ar_fail_features
                    else -> R.string.ar_scanning
                })
                !st.surfacesFound && st.centerKind == null -> stringResource(R.string.ar_scanning)
                else -> null
            }
            if (hint != null) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xCC000000)) {
                Text(hint, color = Color.White, modifier = Modifier.padding(10.dp))
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChips(listOf(stringResource(R.string.ar_mode_length), stringResource(R.string.ar_mode_area)), mode) { mode = it }
                val centerQ = ArQuality.of(st.tracking, st.centerKind, st.centerDistance)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ar_camera_distance, st.centerDistance?.let { formatLength(it) } ?: "—"),
                        modifier = Modifier.weight(1f))
                    QualityBadge(centerQ)
                }
                val pts = st.points
                val worst = pts.fold(if (pts.isEmpty()) Quality.UNRELIABLE else Quality.GOOD) { q, p ->
                    Quality.worst(q, ArQuality.of(true, p.kind, p.placedAtDistance))
                }
                val worlds = pts.map { it.world }
                when {
                    pts.size < 2 -> Text(stringResource(R.string.ar_hint_points), style = MaterialTheme.typography.bodySmall)
                    mode == 0 -> {
                        InfoRow(stringResource(R.string.ar_total_length), formatLength(MeasureMath.pathLength(worlds)))
                        val dy = abs(worlds.last().y - worlds[worlds.size - 2].y)
                        InfoRow(stringResource(R.string.ar_vertical_diff), formatLength(dy))
                    }
                    else -> {
                        InfoRow(stringResource(R.string.ar_area), if (pts.size >= 3) "${fmt(MeasureMath.polygonArea(worlds), 2)} m²" else "—")
                        InfoRow(stringResource(R.string.ar_perimeter), formatLength(MeasureMath.perimeter(worlds)))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { controller.post(ArAction.ADD_AT_CENTER) }, enabled = st.centerKind != null) {
                        Text(stringResource(R.string.ar_add_point))
                    }
                    TextButton(onClick = { controller.post(ArAction.UNDO) }, enabled = pts.isNotEmpty()) { Text(stringResource(R.string.ar_undo)) }
                    TextButton(onClick = { controller.post(ArAction.CLEAR) }, enabled = pts.isNotEmpty()) { Text(stringResource(R.string.reset)) }
                }
                if (pts.size >= 2) SaveButton { note ->
                    save(if (mode == 1 && pts.size >= 3)
                        tool.record(MeasureMath.polygonArea(worlds), "m²", worst, "arcore_area",
                            details = "perimeter ${formatLength(MeasureMath.perimeter(worlds))}", note = note)
                    else tool.record(MeasureMath.pathLength(worlds), "m", worst, "arcore_length",
                        details = "${pts.size} points", note = note))
                }
            }
        }
    }
}
