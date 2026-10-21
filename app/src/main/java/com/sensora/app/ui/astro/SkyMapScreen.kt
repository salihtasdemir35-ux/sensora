package com.sensora.app.ui.astro

import android.hardware.GeomagneticField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.astro.*
import com.sensora.app.core.sensor.SensorReaders
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Catalogue converted once to equatorial unit vectors of date (precession applied for the session). */
private class SkyModel(catalog: SkyCatalog, jd: Double) {
    val stars = catalog.stars
    val starEq = DoubleArray(stars.size * 3)
    val dso = catalog.deepSky
    val dsoEq = DoubleArray(dso.size * 3)
    val cons = catalog.constellations
    val labelEq = DoubleArray(cons.size * 3)
    val segEq: DoubleArray

    init {
        fun put(arr: DoubleArray, i: Int, e: Equatorial) {
            val v = Coords.unitVector(Coords.precessFromJ2000(e, jd)); arr[i * 3] = v[0]; arr[i * 3 + 1] = v[1]; arr[i * 3 + 2] = v[2]
        }
        stars.forEachIndexed { i, s -> put(starEq, i, s.eq) }
        dso.forEachIndexed { i, d -> put(dsoEq, i, d.eq) }
        cons.forEachIndexed { i, c -> put(labelEq, i, c.label) }
        val segs = ArrayList<Equatorial>()
        cons.forEach { c -> c.lines.forEach { poly -> for (k in 0 until poly.size - 1) { segs.add(poly[k]); segs.add(poly[k + 1]) } } }
        segEq = DoubleArray(segs.size * 3)
        segs.forEachIndexed { i, e -> put(segEq, i, e) }
    }
}

/** Everything converted to the local horizon frame (east, north, up) for one instant. */
private class SkyFrame(model: SkyModel, obs: Observer, epochMs: Long) {
    val star = FloatArray(model.starEq.size)
    val dso = FloatArray(model.dsoEq.size)
    val label = FloatArray(model.labelEq.size)
    val seg = FloatArray(model.segEq.size)
    val bodies: List<Sky.BodyNow> = Body.entries.map { Sky.body(it, obs, epochMs) }

    init {
        val jd = AstroTime.julianDay(epochMs)
        val frame = HorizonFrame(obs.latDeg, AstroTime.lstDeg(jd, obs.lonDeg))
        val tmp = DoubleArray(3)
        fun conv(src: DoubleArray, dst: FloatArray) {
            var i = 0
            while (i < src.size) {
                frame.toHorizon(src[i], src[i + 1], src[i + 2], tmp)
                dst[i] = tmp[0].toFloat(); dst[i + 1] = tmp[1].toFloat(); dst[i + 2] = tmp[2].toFloat()
                i += 3
            }
        }
        conv(model.starEq, star); conv(model.dsoEq, dso); conv(model.labelEq, label); conv(model.segEq, seg)
    }

    fun bodyVector(b: Sky.BodyNow): FloatArray {
        val h = b.horizontal
        val ca = cosD(h.altDeg)
        return floatArrayOf((ca * sinD(h.azDeg)).toFloat(), (ca * cosD(h.azDeg)).toFloat(), sinD(h.altDeg).toFloat())
    }
}

private fun sinD(d: Double) = sin(Math.toRadians(d))
private fun cosD(d: Double) = cos(Math.toRadians(d))

/** View basis: screen-right, screen-up and forward, all in horizon coordinates. */
private class Basis(val r: FloatArray, val u: FloatArray, val f: FloatArray)

private class Projector(val basis: Basis, val cx: Float, val cy: Float, val scale: Float) {
    var x = 0f; var y = 0f
    fun project(vx: Float, vy: Float, vz: Float): Boolean {
        val c = vx * basis.f[0] + vy * basis.f[1] + vz * basis.f[2]
        if (c < -0.3f) return false
        val a = vx * basis.r[0] + vy * basis.r[1] + vz * basis.r[2]
        val b = vx * basis.u[0] + vy * basis.u[1] + vz * basis.u[2]
        val k = 2f / (1f + c)
        x = cx + a * k * scale; y = cy - b * k * scale
        return true
    }
}

private sealed interface SkyObject {
    data class OfBody(val body: Body) : SkyObject
    data class OfStar(val index: Int) : SkyObject
    data class OfDso(val index: Int) : SkyObject
}

private fun manualBasis(azDeg: Float, altDeg: Float): Basis {
    val ca = cosD(altDeg.toDouble()).toFloat()
    val f = floatArrayOf(ca * sinD(azDeg.toDouble()).toFloat(), ca * cosD(azDeg.toDouble()).toFloat(), sinD(altDeg.toDouble()).toFloat())
    var r = floatArrayOf(f[1], -f[0], 0f)
    val n = sqrt(r[0] * r[0] + r[1] * r[1])
    r = if (n < 1e-4f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(r[0] / n, r[1] / n, 0f)
    val u = floatArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    return Basis(r, u, f)
}

/** Basis from the phone's rotation matrix; magnetic frame turned to true north by the declination. */
private fun sensorBasis(m: FloatArray, declDeg: Double): Basis {
    val cd = cosD(declDeg).toFloat(); val sd = sinD(declDeg).toFloat()
    fun turn(x: Float, y: Float, z: Float) = floatArrayOf(x * cd + y * sd, -x * sd + y * cd, z)
    fun norm(v: FloatArray): FloatArray { val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f); return floatArrayOf(v[0] / n, v[1] / n, v[2] / n) }
    return Basis(norm(turn(m[0], m[3], m[6])), norm(turn(m[1], m[4], m[7])), norm(turn(-m[2], -m[5], -m[8])))
}

private fun starColor(bv: Double?, night: Boolean): Color = when {
    night -> Color(0xFFFF5252)
    bv == null -> Color.White
    bv < 0.0 -> Color(0xFFAFC4FF)
    bv < 0.5 -> Color(0xFFF4F6FF)
    bv < 1.0 -> Color(0xFFFFE2B8)
    else -> Color(0xFFFFC08A)
}

@Composable
fun SkyMapScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val observer = rememberObserver()
        if (observer == null) {
            ScreenColumn(padding) { ObserverPanel(null) }
            return@ToolScaffold
        }
        val catalog = rememberSkyCatalog()
        if (catalog == null) {
            ScreenColumn(padding) { WaitingCard() }
            return@ToolScaffold
        }
        SkyMapContent(padding, observer, catalog)
    }
}

@Composable
private fun SkyMapContent(padding: PaddingValues, observer: Observer, catalog: SkyCatalog) {
    val sm = rememberSensorManager()
    val density = LocalDensity.current.density
    val hasOrientation = remember(sm) { SensorReaders.hasOrientation(sm) }
    var follow by rememberSaveable { mutableStateOf(hasOrientation) }
    var showLines by rememberSaveable { mutableStateOf(true) }
    var showNames by rememberSaveable { mutableStateOf(true) }
    var showDso by rememberSaveable { mutableStateOf(true) }
    var showBelow by rememberSaveable { mutableStateOf(false) }
    var night by rememberSaveable { mutableStateOf(false) }
    var fov by rememberSaveable { mutableStateOf(100f) }
    var viewAz by rememberSaveable { mutableStateOf(180f) }
    var viewAlt by rememberSaveable { mutableStateOf(35f) }
    var shiftHours by rememberSaveable { mutableStateOf(0f) }
    var selected by remember { mutableStateOf<SkyObject?>(null) }
    val now = rememberNow(1000)
    val epoch = now + (shiftHours * 3_600_000).toLong()
    val lang = Locale.getDefault().language
    val points = stringArrayResource(R.array.compass_points)
    val planetNames = Body.entries.associateWith { stringResource(bodyName(it)) }

    val model = remember(catalog, observer) { SkyModel(catalog, AstroTime.julianDay(System.currentTimeMillis())) }
    val frame = remember(model, observer, epoch / 1000) { SkyFrame(model, observer, epoch) }
    val declination = remember(observer) {
        GeomagneticField(observer.latDeg.toFloat(), observer.lonDeg.toFloat(), observer.elevationM.toFloat(), System.currentTimeMillis()).declination.toDouble()
    }
    val rotFlow = remember(sm, follow) { if (follow) SensorReaders.rotationMatrix(sm) else emptyFlow() }
    val rot by rotFlow.collectAsStateWithLifecycle(initialValue = null)
    val basis = rot?.takeIf { follow }?.let { sensorBasis(it, declination) } ?: manualBasis(viewAz, viewAlt)
    val projectorRef = remember { arrayOfNulls<Projector>(1) }
    val measurer = rememberTextMeasurer()

    val bg = if (night) Color.Black else Color(0xFF060B18)
    val lineColor = if (night) Color(0x66FF1744) else Color(0x5580A8FF)
    val labelColor = if (night) Color(0xFFFF5252) else Color(0xFFB0BEC5)
    val horizonColor = if (night) Color(0xFFB71C1C) else Color(0xFF4CAF50)
    val dsoColor = if (night) Color(0xFFFF8A80) else Color(0xFF80CBC4)
    val limitMag = when { fov > 110 -> 4.5; fov > 60 -> 5.2; else -> 6.0 }

    fun hitTest(o: Offset): SkyObject? {
        val p = projectorRef[0] ?: return null
        var best: SkyObject? = null
        var bestD = 36f * density
        fun consider(vx: Float, vy: Float, vz: Float, obj: SkyObject, bonus: Float = 1f) {
            if (!showBelow && vz < 0f) return
            if (!p.project(vx, vy, vz)) return
            val dx = p.x - o.x; val dy = p.y - o.y
            val d = sqrt(dx * dx + dy * dy) / bonus
            if (d < bestD) { bestD = d; best = obj }
        }
        frame.bodies.forEach { b -> val v = frame.bodyVector(b); consider(v[0], v[1], v[2], SkyObject.OfBody(b.position.body), 1.5f) }
        if (showDso) model.dso.indices.forEach { i -> consider(frame.dso[i * 3], frame.dso[i * 3 + 1], frame.dso[i * 3 + 2], SkyObject.OfDso(i)) }
        model.stars.indices.forEach { i ->
            if (model.stars[i].mag <= minOf(limitMag, 4.0)) consider(frame.star[i * 3], frame.star[i * 3 + 1], frame.star[i * 3 + 2], SkyObject.OfStar(i))
        }
        return best
    }

    val currentHitTest by rememberUpdatedState<(Offset) -> SkyObject?>({ o -> hitTest(o) })

    Box(Modifier.fillMaxSize().padding(padding).background(bg)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(follow) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        fov = (fov / zoom).coerceIn(15f, 150f)
                        if (!follow) {
                            val degPerPx = fov / size.width
                            viewAz = ((viewAz - pan.x * degPerPx) % 360f + 360f) % 360f
                            viewAlt = (viewAlt + pan.y * degPerPx).coerceIn(-85f, 89f)
                        }
                    }
                }
                .pointerInput(Unit) { detectTapGestures { o -> selected = currentHitTest(o) } },
        ) {
            val scale = (size.minDimension / 2f) / (2f * tan(Math.toRadians(fov / 4.0)).toFloat())
            val p = Projector(basis, size.width / 2f, size.height / 2f, scale)
            projectorRef[0] = p
            drawSky(p, frame, model, lang, points, planetNames, measurer, density, limitMag,
                showLines, showNames, showDso, showBelow, night, lineColor, labelColor, horizonColor, dsoColor, selected)
            if (follow) {
                drawCircle(labelColor.copy(alpha = 0.5f), 14f * density, center, style = Stroke(1.5f * density))
            }
        }

        Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text(
                (if (follow) stringResource(R.string.sky_follow) else stringResource(R.string.sky_manual)) +
                    " · " + stringResource(R.string.sky_fov, fov.toInt().toString()) +
                    (if (shiftHours != 0f) " · " + formatTime(epoch) else ""),
                color = labelColor, style = MaterialTheme.typography.labelMedium,
            )
            if (!hasOrientation) Text(stringResource(R.string.sky_no_orientation), color = labelColor, style = MaterialTheme.typography.labelSmall)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 720.dp).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            selected?.let { SelectedCard(it, model, observer, epoch, planetNames, lang) { selected = null } }
            val sunVisible = frame.bodies.first { it.position.body == Body.SUN }.horizontal.altDeg > -1.0
            if (sunVisible) WarningCard(stringResource(R.string.sky_sun_warning))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasOrientation) FilterChip(follow, { follow = !follow }, label = { Text(stringResource(R.string.sky_follow)) })
                        FilterChip(showLines, { showLines = !showLines }, label = { Text(stringResource(R.string.sky_lines)) })
                        FilterChip(showNames, { showNames = !showNames }, label = { Text(stringResource(R.string.sky_names)) })
                        FilterChip(showDso, { showDso = !showDso }, label = { Text(stringResource(R.string.sky_dso)) })
                        FilterChip(showBelow, { showBelow = !showBelow }, label = { Text(stringResource(R.string.sky_below)) })
                        FilterChip(night, { night = !night }, label = { Text(stringResource(R.string.sky_night)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sky_time_shift, String.format(Locale.getDefault(), "%+.1f h", shiftHours)),
                            style = MaterialTheme.typography.labelMedium)
                        Slider(value = shiftHours, onValueChange = { shiftHours = (it * 2).toInt() / 2f }, valueRange = -12f..12f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        TextButton(onClick = { shiftHours = 0f }) { Text(stringResource(R.string.sky_time_now)) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSky(
    p: Projector, frame: SkyFrame, model: SkyModel, lang: String, points: Array<String>, planetNames: Map<Body, String>,
    measurer: androidx.compose.ui.text.TextMeasurer, density: Float, limitMag: Double,
    showLines: Boolean, showNames: Boolean, showDso: Boolean, showBelow: Boolean, night: Boolean,
    lineColor: Color, labelColor: Color, horizonColor: Color, dsoColor: Color, selected: SkyObject?,
) {
    val small = TextStyle(color = labelColor, fontSize = 11.sp)
    fun label(text: String, x: Float, y: Float, style: TextStyle = small) {
        if (x < -50 || y < -20 || x > size.width + 50 || y > size.height + 20) return
        drawText(measurer.measure(text, style), topLeft = Offset(x + 6f * density, y - 7f * density))
    }

    // Horizon line and cardinal points.
    var px = 0f; var py = 0f; var prev = false
    for (az in 0..360 step 3) {
        val a = Math.toRadians(az.toDouble())
        val ok = p.project(sin(a).toFloat(), cos(a).toFloat(), 0f)
        if (ok && prev && kotlin.math.abs(p.x - px) < size.width) drawLine(horizonColor, Offset(px, py), Offset(p.x, p.y), 2f * density)
        prev = ok; px = p.x; py = p.y
    }
    listOf(0, 4, 8, 12).forEach { i ->
        val a = Math.toRadians(i * 22.5)
        if (p.project(sin(a).toFloat(), cos(a).toFloat(), 0f)) label(points[i], p.x - 10f * density, p.y + 18f * density, small.copy(color = horizonColor, fontSize = 14.sp))
    }

    if (showLines) {
        val s = frame.seg
        var i = 0
        while (i < s.size) {
            val visible = showBelow || (s[i + 2] > -0.05f && s[i + 5] > -0.05f)
            if (visible && p.project(s[i], s[i + 1], s[i + 2])) {
                val x1 = p.x; val y1 = p.y
                if (p.project(s[i + 3], s[i + 4], s[i + 5])) drawLine(lineColor, Offset(x1, y1), Offset(p.x, p.y), 1.2f * density)
            }
            i += 6
        }
    }

    if (showNames) {
        model.cons.forEachIndexed { i, c ->
            val l = frame.label
            if ((showBelow || l[i * 3 + 2] > 0f) && p.project(l[i * 3], l[i * 3 + 1], l[i * 3 + 2]))
                label(c.name(lang), p.x, p.y, small.copy(color = lineColor.copy(alpha = 0.9f), fontSize = 10.sp))
        }
    }

    if (showDso) {
        model.dso.forEachIndexed { i, d ->
            val z = frame.dso[i * 3 + 2]
            if ((showBelow || z > 0f) && p.project(frame.dso[i * 3], frame.dso[i * 3 + 1], z)) {
                drawCircle(dsoColor.copy(alpha = if (z > 0) 0.9f else 0.35f), 4.5f * density, Offset(p.x, p.y), style = Stroke(1.2f * density))
                if (showNames && (d.mag < 5.5 || limitMag >= 6.0)) label(d.id, p.x, p.y, small.copy(color = dsoColor, fontSize = 9.sp))
            }
        }
    }

    val st = frame.star
    model.stars.forEachIndexed { i, s ->
        if (s.mag > limitMag) return@forEachIndexed
        val z = st[i * 3 + 2]
        if (!showBelow && z < 0f) return@forEachIndexed
        if (!p.project(st[i * 3], st[i * 3 + 1], z)) return@forEachIndexed
        if (p.x < -10 || p.y < -10 || p.x > size.width + 10 || p.y > size.height + 10) return@forEachIndexed
        val radius = ((3.4 - 0.55 * s.mag).coerceAtLeast(0.6) * density).toFloat()
        drawCircle(starColor(s.bv, night).copy(alpha = if (z > 0) 1f else 0.3f), radius, Offset(p.x, p.y))
        if (showNames && s.name.isNotEmpty() && s.mag < (if (limitMag >= 6.0) 3.0 else 1.6)) label(s.name, p.x, p.y)
    }

    frame.bodies.forEach { b ->
        val v = frame.bodyVector(b)
        if (!showBelow && v[2] < -0.01f) return@forEach
        if (!p.project(v[0], v[1], v[2])) return@forEach
        val body = b.position.body
        val r = when (body) { Body.SUN -> 11f; Body.MOON -> 10f; Body.VENUS, Body.JUPITER -> 5.5f; else -> 4.5f } * density
        val color = if (night) Color(0xFFFF8A80) else bodyColor(body)
        drawCircle(color.copy(alpha = if (v[2] > 0) 1f else 0.4f), r, Offset(p.x, p.y))
        label(planetNames.getValue(body), p.x + r / 2, p.y, small.copy(color = color, fontSize = 12.sp))
    }

    selected?.let { sel ->
        val v: FloatArray? = when (sel) {
            is SkyObject.OfBody -> frame.bodies.firstOrNull { it.position.body == sel.body }?.let { frame.bodyVector(it) }
            is SkyObject.OfStar -> floatArrayOf(st[sel.index * 3], st[sel.index * 3 + 1], st[sel.index * 3 + 2])
            is SkyObject.OfDso -> floatArrayOf(frame.dso[sel.index * 3], frame.dso[sel.index * 3 + 1], frame.dso[sel.index * 3 + 2])
        }
        if (v != null && p.project(v[0], v[1], v[2])) drawCircle(Color(0xFFFFD54F), 16f * density, Offset(p.x, p.y), style = Stroke(2f * density))
    }
}

@Composable
private fun SelectedCard(
    sel: SkyObject, model: SkyModel, observer: Observer, epoch: Long,
    planetNames: Map<Body, String>, lang: String, onClose: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val info = remember(sel, observer, epoch / 60_000) {
        when (sel) {
            is SkyObject.OfBody -> {
                val now = Sky.body(sel.body, observer, epoch)
                val h0 = when (sel.body) { Body.SUN -> RiseSet.H0_SUN; Body.MOON -> RiseSet.H0_MOON; else -> RiseSet.H0_STAR }
                Triple(now.horizontal, RiseSet.events(dayStart, dayStart + AstroTime.DAY_MS, h0, Sky.bodyAltitude(sel.body, observer)), now.position)
            }
            is SkyObject.OfStar -> {
                val eq = model.stars[sel.index].eq
                Triple(Sky.fixed(eq, observer, epoch), RiseSet.events(dayStart, dayStart + AstroTime.DAY_MS, RiseSet.H0_STAR, Sky.fixedAltitude(eq, observer, epoch)), null)
            }
            is SkyObject.OfDso -> {
                val eq = model.dso[sel.index].eq
                Triple(Sky.fixed(eq, observer, epoch), RiseSet.events(dayStart, dayStart + AstroTime.DAY_MS, RiseSet.H0_STAR, Sky.fixedAltitude(eq, observer, epoch)), null)
            }
        }
    }
    val (h, ev, pos) = info
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (sel) {
                    is SkyObject.OfBody -> planetNames.getValue(sel.body)
                    is SkyObject.OfStar -> model.stars[sel.index].let { s -> listOf(s.name, s.designation).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "HIP ${s.hip}" } }
                    is SkyObject.OfDso -> model.dso[sel.index].displayName
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
            }
            when (sel) {
                is SkyObject.OfStar -> InfoRow(stringResource(R.string.obj_type), stringResource(R.string.obj_star))
                is SkyObject.OfDso -> {
                    val d = model.dso[sel.index]
                    InfoRow(stringResource(R.string.obj_type), stringResource(dsoTypeName(d.type)))
                    d.distanceLy?.let { InfoRow(stringResource(R.string.obj_distance), stringResource(R.string.obj_ly, formatBig(it))) }
                    if (d.size.isNotBlank()) InfoRow(stringResource(R.string.obj_size), "${d.size}′")
                    InfoRow(stringResource(R.string.obj_method), stringResource(methodName(Visibility.method(d.mag, isExtended(d.type)))))
                }
                is SkyObject.OfBody -> pos?.let { bp ->
                    if (bp.body != Body.SUN) InfoRow(stringResource(R.string.obj_elongation), "${fmt(bp.elongationDeg, 0)}°")
                    if (bp.body != Body.SUN) InfoRow(stringResource(R.string.obj_illum), "${fmt(bp.illuminatedFraction * 100, 0)} %")
                    InfoRow(stringResource(R.string.obj_distance),
                        if (bp.body == Body.MOON) stringResource(R.string.obj_km, formatBig(bp.distance * 6378.14))
                        else stringResource(R.string.obj_au, fmt(bp.distance, 3)))
                    if (bp.body != Body.SUN && bp.body != Body.MOON)
                        InfoRow(stringResource(R.string.obj_method), stringResource(methodName(Visibility.method(bp.magnitude, false))))
                }
            }
            val mag = when (sel) {
                is SkyObject.OfBody -> pos?.magnitude
                is SkyObject.OfStar -> model.stars[sel.index].mag
                is SkyObject.OfDso -> model.dso[sel.index].mag.takeIf { it < 50 }
            }
            mag?.let { InfoRow(stringResource(R.string.obj_magnitude), fmt(it, 1)) }
            InfoRow(stringResource(R.string.obj_altitude), "${fmt(h.altDeg + Coords.refractionDeg(h.altDeg), 1)}°")
            InfoRow(stringResource(R.string.obj_azimuth), "${fmt(h.azDeg, 0)}° ${cardinalName(h.azDeg)}")
            when {
                ev.alwaysUp -> Text(stringResource(R.string.obj_always_up), style = MaterialTheme.typography.bodySmall)
                ev.alwaysDown -> Text(stringResource(R.string.obj_never_up), style = MaterialTheme.typography.bodySmall)
                else -> Text("${stringResource(R.string.obj_rise)} ${formatTime(ev.rise)} · ${stringResource(R.string.obj_transit)} ${formatTime(ev.transit)} · ${stringResource(R.string.obj_set)} ${formatTime(ev.set)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (h.altDeg < 0) Text(stringResource(R.string.obj_below), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

fun formatBig(v: Double): String = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(v)
