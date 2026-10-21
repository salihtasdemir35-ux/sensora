package com.sensora.app.ui.astro

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.astro.*
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private data class PlanetRow(val body: Body, val now: Sky.BodyNow, val today: RiseSetEvents, val tonight: TargetVisibility?)
private data class DsoRow(val dso: DeepSky, val vis: TargetVisibility)
private data class ShowerRow(val shower: MeteorShower, val active: Boolean, val peakMs: Long, val radiantMaxAlt: Double?)
private data class TonightPlan(
    val window: NightWindow?,
    val planets: List<PlanetRow>,
    val dsos: List<DsoRow>,
    val moon: MoonPhase,
    val moonUpTonight: Boolean,
    val conjunctions: List<TonightPlanner.Conjunction>,
    val showers: List<ShowerRow>,
)

private val PLANETS = listOf(Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN, Body.URANUS, Body.NEPTUNE)

private fun buildPlan(obs: Observer, catalog: SkyCatalog, nowMs: Long, zone: ZoneId): TonightPlan {
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val window = TonightPlanner.nightWindow(obs, nowMs)
    val planets = PLANETS.map { b ->
        val alt = Sky.bodyAltitude(b, obs)
        PlanetRow(
            b, Sky.body(b, obs, nowMs),
            RiseSet.events(dayStart, dayStart + AstroTime.DAY_MS, RiseSet.H0_STAR, alt),
            window?.let { w -> TonightPlanner.visibility(w, nowMs) { t -> Sky.body(b, obs, t).horizontal } },
        )
    }
    val dsos = if (window == null) emptyList() else catalog.deepSky.filter { it.mag <= 9.0 }.mapNotNull { d ->
        val eq = Coords.precessFromJ2000(d.eq, AstroTime.julianDay(nowMs))
        val vis = TonightPlanner.visibility(window, nowMs) { t ->
            Coords.toHorizontal(eq, obs.latDeg, AstroTime.lstDeg(AstroTime.julianDay(t), obs.lonDeg))
        }
        if (vis.maxAltDeg >= 25.0) DsoRow(d, vis) else null
    }.sortedBy { it.dso.mag }.take(30)
    val moonUp = window != null && TonightPlanner.visibility(window, nowMs) { t -> Sky.body(Body.MOON, obs, t).horizontal }.maxAltDeg > 0
    val showers = MeteorShowers.all.mapNotNull { s ->
        var peak = LocalDate.of(today.year, s.peakMonth, s.peakDay)
        if (peak.isBefore(today.minusDays(20))) peak = peak.plusYears(1)
        val active = MeteorShowers.isActive(s, today.monthValue, today.dayOfMonth)
        val daysToPeak = java.time.temporal.ChronoUnit.DAYS.between(today, peak)
        if (!active && daysToPeak !in 0..45) return@mapNotNull null
        val radiant = window?.let { w ->
            val eq = Coords.precessFromJ2000(s.radiant, AstroTime.julianDay(nowMs))
            TonightPlanner.visibility(w, nowMs) { t -> Coords.toHorizontal(eq, obs.latDeg, AstroTime.lstDeg(AstroTime.julianDay(t), obs.lonDeg)) }.maxAltDeg
        }
        ShowerRow(s, active, peak.atStartOfDay(zone).toInstant().toEpochMilli(), radiant)
    }.sortedBy { it.peakMs }
    val mid = window?.let { (it.startMs + it.endMs) / 2 } ?: nowMs
    return TonightPlan(window, planets, dsos, MoonInfo.phase(nowMs), moonUp, TonightPlanner.conjunctions(mid), showers)
}

@StringRes
private fun showerName(id: String): Int = when (id) {
    "quadrantids" -> R.string.shower_quadrantids
    "lyrids" -> R.string.shower_lyrids
    "eta_aquariids" -> R.string.shower_eta_aquariids
    "delta_aquariids" -> R.string.shower_delta_aquariids
    "perseids" -> R.string.shower_perseids
    "draconids" -> R.string.shower_draconids
    "orionids" -> R.string.shower_orionids
    "leonids" -> R.string.shower_leonids
    "geminids" -> R.string.shower_geminids
    else -> R.string.shower_ursids
}

@Composable
fun TonightScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val observer = rememberObserver()
        val catalog = rememberSkyCatalog()
        val zone = ZoneId.systemDefault()
        val minute = rememberNow(60_000) / 600_000   // recompute every 10 minutes
        var plan by remember { mutableStateOf<TonightPlan?>(null) }
        var detail by remember { mutableStateOf<DeepSky?>(null) }
        LaunchedEffect(observer, catalog, minute) {
            if (observer != null && catalog != null) {
                plan = withContext(Dispatchers.Default) { buildPlan(observer, catalog, System.currentTimeMillis(), zone) }
            }
        }
        ScreenColumn(padding) {
            ObserverPanel(observer)
            if (observer == null) return@ScreenColumn
            val p = plan
            if (p == null) { WaitingCard(); return@ScreenColumn }

            InfoCard {
                Text(stringResource(R.string.tonight_window), style = MaterialTheme.typography.titleMedium)
                val w = p.window
                if (w == null) {
                    Text(stringResource(R.string.tonight_no_dark))
                } else {
                    Text(stringResource(R.string.tonight_window_fmt, formatTime(w.startMs), formatTime(w.endMs)),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(when (w.darknessDeg) {
                        SunDay.ASTRONOMICAL -> R.string.tonight_dark_astro
                        SunDay.NAUTICAL -> R.string.tonight_dark_nautical
                        else -> R.string.tonight_dark_civil
                    }), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonPhaseIcon(p.moon, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("${stringResource(moonPhaseName(p.moon.phaseIndex))} · ${fmt(p.moon.illuminated * 100, 0)} %")
                }
                if (p.moonUpTonight && p.moon.illuminated > 0.5)
                    WarningCard(stringResource(R.string.tonight_moon_bright, fmt(p.moon.illuminated * 100, 0)))
            }

            Text(stringResource(R.string.tonight_planets), style = MaterialTheme.typography.titleLarge)
            p.planets.forEach { row -> PlanetCard(row) }

            if (p.conjunctions.isNotEmpty()) {
                InfoCard {
                    Text(stringResource(R.string.tonight_conjunctions), style = MaterialTheme.typography.titleMedium)
                    p.conjunctions.forEach { c ->
                        Text(stringResource(R.string.tonight_conj_fmt, stringResource(bodyName(c.a)), stringResource(bodyName(c.b)), fmt(c.separationDeg, 1)))
                    }
                }
            }

            Text(stringResource(R.string.tonight_dso), style = MaterialTheme.typography.titleLarge)
            if (p.dsos.isEmpty()) Text(stringResource(R.string.tonight_dso_none))
            p.dsos.forEach { row ->
                Card(Modifier.fillMaxWidth().clickable { detail = row.dso }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.dso.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${stringResource(dsoTypeName(row.dso.type))} · mag ${fmt(row.dso.mag, 1)} · " +
                            stringResource(methodName(Visibility.method(row.dso.mag, isExtended(row.dso.type)))),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.tonight_best, formatTime(row.vis.bestTimeMs), fmt(row.vis.maxAltDeg, 0), cardinalName(row.vis.azAtBestDeg)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (p.showers.isNotEmpty()) {
                InfoCard {
                    Text(stringResource(R.string.tonight_showers), style = MaterialTheme.typography.titleMedium)
                    p.showers.forEach { s ->
                        Text(stringResource(showerName(s.shower.id)), fontWeight = FontWeight.SemiBold)
                        Text(if (s.active) stringResource(R.string.tonight_shower_active, formatDate(s.peakMs), s.shower.zhr)
                            else stringResource(R.string.tonight_shower_upcoming, formatDate(s.peakMs)), style = MaterialTheme.typography.bodySmall)
                        s.radiantMaxAlt?.let { if (it < 10) Text(stringResource(R.string.tonight_radiant_low), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Text(stringResource(R.string.tonight_weather_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tonight_eclipse_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.astro_times_zone), style = MaterialTheme.typography.bodySmall)
        }
        detail?.let { d -> DsoDialog(d) { detail = null } }
    }
}

@Composable
private fun PlanetCard(row: PlanetRow) {
    val pos = row.now.position
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(bodyName(row.body)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("mag ${fmt(pos.magnitude, 1)}", style = MaterialTheme.typography.labelLarge)
            }
            val h = row.now.horizontal
            Text(if (h.altDeg > 0) "${stringResource(R.string.tonight_now_up)} · ${fmt(h.altDeg, 0)}° ${cardinalName(h.azDeg)}"
                else stringResource(R.string.tonight_now_down), style = MaterialTheme.typography.bodySmall)
            Text("${stringResource(R.string.obj_rise)} ${formatTime(row.today.rise)} · ${stringResource(R.string.obj_transit)} ${formatTime(row.today.transit)} · ${stringResource(R.string.obj_set)} ${formatTime(row.today.set)}",
                style = MaterialTheme.typography.bodySmall)
            val v = row.tonight
            when {
                pos.elongationDeg < 15 -> Text(stringResource(R.string.tonight_near_sun), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                v != null && v.maxAltDeg >= 10 -> Text(stringResource(R.string.tonight_best, formatTime(v.bestTimeMs), fmt(v.maxAltDeg, 0), cardinalName(v.azAtBestDeg)) +
                    " · " + stringResource(methodName(Visibility.method(pos.magnitude, false))),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                else -> Text(stringResource(R.string.tonight_not_visible), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DsoDialog(d: DeepSky, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(d.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(stringResource(R.string.obj_type), stringResource(dsoTypeName(d.type)))
                if (d.designation.isNotBlank()) InfoRow("", d.designation)
                InfoRow(stringResource(R.string.obj_magnitude), fmt(d.mag, 1))
                if (d.size.isNotBlank()) InfoRow(stringResource(R.string.obj_size), "${d.size}′")
                d.distanceLy?.let { InfoRow(stringResource(R.string.obj_distance), stringResource(R.string.obj_ly, formatBig(it))) }
                InfoRow(stringResource(R.string.obj_method), stringResource(methodName(Visibility.method(d.mag, isExtended(d.type)))))
                Text(stringResource(R.string.dso_method_note), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
