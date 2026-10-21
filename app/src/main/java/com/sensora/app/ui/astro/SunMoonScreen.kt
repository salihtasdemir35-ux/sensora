package com.sensora.app.ui.astro

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private data class SunMoonDay(
    val sun: SunDay,
    val moon: RiseSetEvents,
    val phase: MoonPhase,
    val nextFull: Long,
    val nextNew: Long,
)

@Composable
fun SunMoonScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        val observer = rememberObserver()
        val zone = ZoneId.systemDefault()
        var offsetDays by rememberSaveable { mutableStateOf(0) }
        var data by remember { mutableStateOf<SunMoonDay?>(null) }
        val date = LocalDate.now(zone).plusDays(offsetDays.toLong())
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        LaunchedEffect(observer, dayStart) {
            data = null
            val o = observer ?: return@LaunchedEffect
            data = withContext(Dispatchers.Default) {
                val noon = dayStart + AstroTime.DAY_MS / 2
                SunMoonDay(
                    SunTimes.compute(o, dayStart),
                    RiseSet.events(dayStart, dayStart + AstroTime.DAY_MS, RiseSet.H0_MOON, Sky.bodyAltitude(Body.MOON, o)),
                    MoonInfo.phase(noon),
                    MoonInfo.next(dayStart, 180.0),
                    MoonInfo.next(dayStart, 0.0),
                )
            }
        }
        ScreenColumn(padding) {
            ObserverPanel(observer)
            if (observer == null) return@ScreenColumn
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offsetDays-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_prev)) }
                Text(formatDate(dayStart + AstroTime.DAY_MS / 2), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (offsetDays != 0) TextButton(onClick = { offsetDays = 0 }) { Text(stringResource(R.string.day_today)) }
                IconButton(onClick = { offsetDays++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next)) }
            }
            val d = data
            if (d == null) { WaitingCard(); return@ScreenColumn }
            val s = d.sun
            InfoCard {
                Text(stringResource(R.string.sun_title), style = MaterialTheme.typography.titleLarge)
                when {
                    s.events.alwaysUp -> Text(stringResource(R.string.sun_polar_day))
                    s.events.alwaysDown -> Text(stringResource(R.string.sun_polar_night))
                    else -> {
                        InfoRow(stringResource(R.string.sun_rise), formatTime(s.events.rise))
                        InfoRow(stringResource(R.string.sun_set), formatTime(s.events.set))
                    }
                }
                InfoRow(stringResource(R.string.sun_noon), formatTime(s.events.transit))
                InfoRow(stringResource(R.string.sun_noon_alt), "${fmt(s.events.transitAltDeg, 1)}°")
                s.dayLengthMs?.let { InfoRow(stringResource(R.string.sun_day_length), formatDuration(it)) }
            }
            InfoCard {
                TwilightHeader()
                TwilightRow(stringResource(R.string.sun_civil), s.risingAt(SunDay.CIVIL), s.settingAt(SunDay.CIVIL))
                TwilightRow(stringResource(R.string.sun_nautical), s.risingAt(SunDay.NAUTICAL), s.settingAt(SunDay.NAUTICAL))
                TwilightRow(stringResource(R.string.sun_astro), s.risingAt(SunDay.ASTRONOMICAL), s.settingAt(SunDay.ASTRONOMICAL))
                HorizontalDivider()
                TwilightRangeRow(stringResource(R.string.sun_blue),
                    s.risingAt(SunDay.CIVIL) to s.risingAt(SunDay.BLUE_GOLDEN), s.settingAt(SunDay.BLUE_GOLDEN) to s.settingAt(SunDay.CIVIL))
                TwilightRangeRow(stringResource(R.string.sun_golden),
                    s.risingAt(SunDay.BLUE_GOLDEN) to s.risingAt(SunDay.GOLDEN_TOP), s.settingAt(SunDay.GOLDEN_TOP) to s.settingAt(SunDay.BLUE_GOLDEN))
            }
            InfoCard {
                Text(stringResource(R.string.moon_title), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonPhaseIcon(d.phase, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(moonPhaseName(d.phase.phaseIndex)), style = MaterialTheme.typography.titleMedium)
                        Text("${stringResource(R.string.obj_illum)}: ${fmt(d.phase.illuminated * 100, 0)} %")
                        Text("${stringResource(R.string.moon_age)}: ${stringResource(R.string.moon_age_days, fmt(d.phase.ageDays, 1))}")
                    }
                }
                when {
                    d.moon.alwaysUp -> Text(stringResource(R.string.obj_always_up))
                    d.moon.alwaysDown -> Text(stringResource(R.string.obj_never_up))
                    else -> {
                        InfoRow(stringResource(R.string.obj_rise), formatTime(d.moon.rise))
                        InfoRow(stringResource(R.string.obj_transit), formatTime(d.moon.transit))
                        InfoRow(stringResource(R.string.obj_set), formatTime(d.moon.set))
                    }
                }
                InfoRow(stringResource(R.string.moon_next_full), formatDate(d.nextFull) + " " + formatTime(d.nextFull))
                InfoRow(stringResource(R.string.moon_next_new), formatDate(d.nextNew) + " " + formatTime(d.nextNew))
            }
            Text(stringResource(R.string.astro_times_zone), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TwilightHeader() {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1.4f))
        Text(stringResource(R.string.sun_morning), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.sun_evening), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TwilightRow(label: String, morning: Long?, evening: Long?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
        Text(formatTime(morning), Modifier.weight(1f))
        Text(formatTime(evening), Modifier.weight(1f))
    }
}

@Composable
private fun TwilightRangeRow(label: String, morning: Pair<Long?, Long?>, evening: Pair<Long?, Long?>) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
        Text("${formatTime(morning.first)}–${formatTime(morning.second)}", Modifier.weight(1f))
        Text("${formatTime(evening.first)}–${formatTime(evening.second)}", Modifier.weight(1f))
    }
}
