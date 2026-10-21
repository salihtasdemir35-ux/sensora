package com.sensora.app.ui.astro

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.astro.Body
import com.sensora.app.core.astro.MoonPhase
import com.sensora.app.core.astro.ObservationMethod
import com.sensora.app.core.astro.Observer
import com.sensora.app.core.astro.SkyCatalog
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

@StringRes
fun bodyName(b: Body): Int = when (b) {
    Body.SUN -> R.string.planet_sun
    Body.MOON -> R.string.planet_moon
    Body.MERCURY -> R.string.planet_mercury
    Body.VENUS -> R.string.planet_venus
    Body.MARS -> R.string.planet_mars
    Body.JUPITER -> R.string.planet_jupiter
    Body.SATURN -> R.string.planet_saturn
    Body.URANUS -> R.string.planet_uranus
    Body.NEPTUNE -> R.string.planet_neptune
}

fun bodyColor(b: Body): Color = when (b) {
    Body.SUN -> Color(0xFFFFD54F)
    Body.MOON -> Color(0xFFE0E0E0)
    Body.MERCURY -> Color(0xFFBCAAA4)
    Body.VENUS -> Color(0xFFFFF59D)
    Body.MARS -> Color(0xFFFF8A65)
    Body.JUPITER -> Color(0xFFFFCC80)
    Body.SATURN -> Color(0xFFFFE082)
    Body.URANUS -> Color(0xFF80DEEA)
    Body.NEPTUNE -> Color(0xFF90CAF9)
}

@StringRes
fun dsoTypeName(type: String): Int = when (type) {
    "oc" -> R.string.type_oc
    "gc" -> R.string.type_gc
    "s", "sd", "e", "i" -> R.string.type_galaxy
    "pn" -> R.string.type_pn
    "en", "rn", "sfr" -> R.string.type_nebula
    "snr" -> R.string.type_snr
    else -> R.string.type_other
}

fun isExtended(type: String) = type != "pos"

@StringRes
fun methodName(m: ObservationMethod): Int = when (m) {
    ObservationMethod.NAKED_EYE -> R.string.method_naked
    ObservationMethod.BINOCULARS -> R.string.method_binoculars
    ObservationMethod.TELESCOPE -> R.string.method_telescope
}

@StringRes
fun moonPhaseName(index: Int): Int = when (index) {
    0 -> R.string.moon_phase_0; 1 -> R.string.moon_phase_1; 2 -> R.string.moon_phase_2; 3 -> R.string.moon_phase_3
    4 -> R.string.moon_phase_4; 5 -> R.string.moon_phase_5; 6 -> R.string.moon_phase_6; else -> R.string.moon_phase_7
}

private val timeFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
private val dateFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

fun formatTime(ms: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFormatter) } ?: "—"

fun formatDate(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(dateFormatter)

fun formatDuration(ms: Long): String = String.format(Locale.getDefault(), "%d:%02d", ms / 3_600_000, (ms / 60_000) % 60)

fun formatLocation(o: Observer): String = String.format(
    Locale.getDefault(), "%.2f° %s, %.2f° %s",
    abs(o.latDeg), if (o.latDeg >= 0) "N" else "S", abs(o.lonDeg), if (o.lonDeg >= 0) "E" else "W",
)

@Composable
fun cardinalName(azDeg: Double): String = stringArrayResource(R.array.compass_points)[OrientationMath.cardinal16(azDeg)]

@Composable
fun rememberObserver(): Observer? {
    val prefs = LocalContext.current.sensoraApp.prefs
    val o by prefs.observer.collectAsStateWithLifecycle()
    return o
}

@Composable
fun rememberSkyCatalog(): SkyCatalog? {
    val app = LocalContext.current.sensoraApp
    val state = produceState<SkyCatalog?>(initialValue = null, app) { value = withContext(Dispatchers.IO) { app.skyCatalog } }
    return state.value
}

/** Current time, refreshed every [periodMs] while composed. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) { delay(periodMs); now = System.currentTimeMillis() }
    }
    return now
}

/** Shows the observing location and lets the user set it from GPS or by hand. */
@Composable
fun ObserverPanel(observer: Observer?) {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    var useGps by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    InfoCard {
        Text(stringResource(R.string.astro_location_title), style = MaterialTheme.typography.titleSmall)
        if (observer == null) {
            Text(stringResource(R.string.astro_location_none))
        } else {
            Text(formatLocation(observer) + " · " +
                stringResource(if (prefs.observerFromGps) R.string.astro_source_gps else R.string.astro_source_manual))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { useGps = true }) { Text(stringResource(R.string.astro_use_gps)) }
            OutlinedButton(onClick = { manual = true }) { Text(stringResource(R.string.astro_enter_manual)) }
        }
        if (useGps) {
            PermissionGate(LOCATION_PERMISSIONS, R.string.location_rationale) {
                LaunchedEffect(Unit) {
                    val loc = SensorReaders.currentLocation(ctx)
                    if (loc != null) prefs.setObserver(Observer(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0), true)
                    useGps = false
                }
                Text(stringResource(R.string.gps_waiting))
            }
        }
        Text(stringResource(R.string.astro_location_note), style = MaterialTheme.typography.bodySmall)
    }
    if (manual) ManualLocationDialog(observer, onDismiss = { manual = false }) { prefs.setObserver(it, false) }
}

@Composable
private fun ManualLocationDialog(current: Observer?, onDismiss: () -> Unit, onConfirm: (Observer) -> Unit) {
    var lat by remember { mutableStateOf(current?.latDeg?.let { "%.4f".format(Locale.US, it) } ?: "") }
    var lon by remember { mutableStateOf(current?.lonDeg?.let { "%.4f".format(Locale.US, it) } ?: "") }
    val la = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lo = lon.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = la != null && lo != null, onClick = { onConfirm(Observer(la!!, lo!!)); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.astro_enter_manual)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lat, { lat = it }, label = { Text(stringResource(R.string.astro_latitude)) }, singleLine = true,
                    isError = lat.isNotBlank() && la == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(lon, { lon = it }, label = { Text(stringResource(R.string.astro_longitude)) }, singleLine = true,
                    isError = lon.isNotBlank() && lo == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            }
        },
    )
}

/** Moon disc with the lit part drawn from the illuminated fraction (northern-hemisphere orientation). */
@Composable
fun MoonPhaseIcon(phase: MoonPhase, size: Dp = 64.dp) {
    val dark = Color(0xFF37474F)
    val lit = Color(0xFFF5F5F5)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(dark, r, c)
        val k = phase.illuminated.toFloat().coerceIn(0f, 1f)
        val start = if (phase.waxing) -90f else 90f
        drawArc(lit, start, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
        val w = r * abs(1f - 2f * k)
        drawOval(if (k < 0.5f) dark else lit, Offset(c.x - w, c.y - r), Size(2 * w, 2 * r))
    }
}
