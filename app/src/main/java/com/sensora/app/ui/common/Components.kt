@file:OptIn(ExperimentalMaterial3Api::class)

package com.sensora.app.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sensora.app.R
import com.sensora.app.SensoraApp
import com.sensora.app.core.model.Quality
import com.sensora.app.data.MeasurementRecord
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.theme.BadColor
import com.sensora.app.ui.theme.GoodColor
import com.sensora.app.ui.theme.LimitedColor
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

val Context.sensoraApp: SensoraApp get() = applicationContext as SensoraApp

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun fmt(v: Double, digits: Int = 1): String =
    if (v.isNaN() || v.isInfinite()) "—" else String.format(Locale.getDefault(), "%.${digits}f", v)

@Composable
fun rememberSensorManager(): SensorManager {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
}

@Composable
fun SimpleScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

@Composable
fun ToolScaffold(
    tool: Tool,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    SimpleScaffold(stringResource(tool.title), onBack, actions = {
        IconButton(onClick = { showHow = true }) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.how_it_works))
        }
    }, content = content)
    if (showHow) HowItWorksDialog(tool) { showHow = false }
}

@Composable
fun HowItWorksDialog(tool: Tool, onDismiss: () -> Unit) {
    val hardware = tool.hardware.map { stringResource(it.label) }.joinToString(", ")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        icon = { Icon(tool.icon, contentDescription = null) },
        title = { Text(stringResource(R.string.how_it_works)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(tool.howItWorks), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.needs, hardware), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(tool.reliability.label), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.quality_legend), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** Centered, scrollable, width-limited column that works on phones, tablets and landscape. */
@Composable
fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun BigValue(value: String, unit: String, caption: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WarningCard(text: String, isError: Boolean = false) {
    val color = if (isError) BadColor else LimitedColor
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Filled.Error else Icons.Filled.Warning, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun UnavailableCard(text: String) = WarningCard(text, isError = true)

@Composable
fun WaitingCard() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.waiting_sensor))
    }
}

@Composable
fun QualityBadge(q: Quality) {
    val label = when (q) { Quality.GOOD -> R.string.quality_good; Quality.LIMITED -> R.string.quality_limited; Quality.UNRELIABLE -> R.string.quality_unreliable }
    val color = when (q) { Quality.GOOD -> GoodColor; Quality.LIMITED -> LimitedColor; Quality.UNRELIABLE -> BadColor }
    val icon = when (q) { Quality.GOOD -> Icons.Filled.CheckCircle; Quality.LIMITED -> Icons.Filled.Warning; Quality.UNRELIABLE -> Icons.Filled.Error }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(label), color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun LineGraph(values: List<Float>, minY: Float? = null, maxY: Float? = null, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val lo = minY ?: values.min()
        var hi = maxY ?: values.max()
        if (hi - lo < 1e-6f) hi = lo + 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = size.height - ((v.coerceIn(lo, hi) - lo) / (hi - lo)) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
fun BarChart(values: List<Float>, goal: Float?, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.isEmpty()) return@Canvas
        val top = maxOf(values.max(), goal ?: 0f).coerceAtLeast(1f)
        val bw = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = v / top * size.height
            drawRect(
                color = if (goal != null && v >= goal) GoodColor else bar,
                topLeft = Offset(i * bw + bw * 0.15f, size.height - h),
                size = Size(bw * 0.7f, h),
            )
        }
        if (goal != null) {
            val y = size.height - goal / top * size.height
            drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
        }
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.08f
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun FigureEightHint(text: String) {
    val t by rememberInfiniteTransition(label = "f8").animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t",
    )
    val outline = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.tertiary
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(72.dp)) {
                val w = size.width / 2.4f
                val path = Path()
                for (i in 0..64) {
                    val s = i / 64.0 * 2 * PI
                    val x = center.x + w * sin(s).toFloat()
                    val y = center.y + w * 0.5f * sin(2 * s).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, outline, style = Stroke(3f))
                drawCircle(dot, 9f, Offset(center.x + w * sin(t), center.y + w * 0.5f * sin(2 * t)))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Shows [content] only when at least one of [permissions] is granted; otherwise explains why it is needed. */
@Composable
fun PermissionGate(permissions: Array<String>, @StringRes rationale: Int, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    fun granted() = permissions.isEmpty() ||
        permissions.any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var isGranted by remember { mutableStateOf(granted()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        isGranted = res.values.any { it }
        denied = !isGranted
    }
    LifecycleResumeEffect(Unit) {
        isGranted = granted()
        onPauseOrDispose { }
    }
    if (isGranted) {
        content()
    } else {
        InfoCard {
            Text(stringResource(rationale), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { launcher.launch(permissions) }) { Text(stringResource(R.string.grant_permission)) }
            if (denied) {
                Text(stringResource(R.string.permission_denied_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }) { Text(stringResource(R.string.open_settings)) }
            }
        }
    }
}

@Composable
fun SaveButton(modifier: Modifier = Modifier, enabled: Boolean = true, onSave: (note: String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Save, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.save))
    }
    if (open) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onSave(note.trim().ifBlank { null }); open = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.save)) },
            text = { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.note_hint)) }) },
        )
    }
}

@Composable
fun NumberInputDialog(title: String, message: String?, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(onConfirm); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
    )
}

@Composable
fun rememberMeasurementSaver(): (MeasurementRecord) -> Unit {
    val ctx = LocalContext.current
    val msg = stringResource(R.string.saved)
    return remember(ctx, msg) {
        { record ->
            val app = ctx.sensoraApp
            app.appScope.launch { app.history.add(record) }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

fun Tool.record(
    value: Double,
    unit: String,
    quality: Quality,
    method: String,
    details: String? = null,
    note: String? = null,
) = MeasurementRecord(
    toolId = id, category = category.name, value = value, unit = unit, details = details,
    method = method, quality = quality.name, note = note,
)

val QualityGoodColor: Color get() = GoodColor
