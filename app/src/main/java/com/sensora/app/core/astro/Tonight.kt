package com.sensora.app.core.astro

enum class ObservationMethod { NAKED_EYE, BINOCULARS, TELESCOPE }

object Visibility {
    /** Rough guide for a moderately dark sky; extended objects need more light-gathering than stars. */
    fun method(magnitude: Double, extended: Boolean): ObservationMethod = when {
        magnitude <= (if (extended) 4.5 else 5.5) -> ObservationMethod.NAKED_EYE
        magnitude <= (if (extended) 7.5 else 9.5) -> ObservationMethod.BINOCULARS
        else -> ObservationMethod.TELESCOPE
    }
}

/** darknessDeg is the Sun-altitude threshold that defines the window: −18, −12 or −6; 0 means no dark window. */
data class NightWindow(val startMs: Long, val endMs: Long, val darknessDeg: Double)

data class TargetVisibility(
    val bestTimeMs: Long,
    val maxAltDeg: Double,
    val azAtBestDeg: Double,
    val altNowDeg: Double,
)

object TonightPlanner {
    private const val STEP = 10 * 60 * 1000L

    /** The night containing [nowMs], or the next one within 30 h. Tries astronomical, then nautical, then civil darkness. */
    fun nightWindow(obs: Observer, nowMs: Long): NightWindow? {
        val alt = Sky.bodyAltitude(Body.SUN, obs)
        for (threshold in listOf(SunDay.ASTRONOMICAL, SunDay.NAUTICAL, SunDay.CIVIL)) {
            var t = nowMs
            var start: Long? = if (alt(nowMs) < threshold) nowMs else null
            val limit = nowMs + 30 * 3_600_000L
            while (t < limit) {
                val a = alt(t)
                if (start == null && a < threshold) start = t
                if (start != null && a >= threshold) return NightWindow(start, t, threshold)
                t += STEP
            }
            if (start != null) return NightWindow(start, limit, threshold)
        }
        return null
    }

    fun visibility(window: NightWindow, nowMs: Long, horizontal: (Long) -> Horizontal): TargetVisibility {
        var bestT = window.startMs; var best = Horizontal(0.0, -90.0)
        var t = window.startMs
        while (t <= window.endMs) {
            val h = horizontal(t)
            if (h.altDeg > best.altDeg) { best = h; bestT = t }
            t += STEP
        }
        return TargetVisibility(bestT, best.altDeg, best.azDeg, horizontal(nowMs).altDeg)
    }

    data class Conjunction(val a: Body, val b: Body, val separationDeg: Double)

    fun conjunctions(epochMs: Long, maxSeparationDeg: Double = 3.0): List<Conjunction> {
        val jd = AstroTime.julianDay(epochMs)
        val bodies = listOf(Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN)
        val pos = bodies.associateWith { SolarSystem.position(it, jd) }
        val out = ArrayList<Conjunction>()
        for (i in bodies.indices) for (j in i + 1 until bodies.size) {
            val a = pos.getValue(bodies[i]); val b = pos.getValue(bodies[j])
            val sep = Coords.separationDeg(a.equatorial, b.equatorial)
            // Skip pairs lost in the Sun's glare.
            if (sep <= maxSeparationDeg && a.elongationDeg > 15 && b.elongationDeg > 15) out.add(Conjunction(bodies[i], bodies[j], sep))
        }
        return out.sortedBy { it.separationDeg }
    }
}

data class MeteorShower(
    val id: String,
    val startMonth: Int, val startDay: Int,
    val peakMonth: Int, val peakDay: Int,
    val endMonth: Int, val endDay: Int,
    val zhr: Int,
    val radiant: Equatorial,
)

/** Major annual showers (IMO working list). Peak dates shift by about a day between years. */
object MeteorShowers {
    val all = listOf(
        MeteorShower("quadrantids", 12, 28, 1, 3, 1, 12, 110, Equatorial(230.0, 49.0)),
        MeteorShower("lyrids", 4, 14, 4, 22, 4, 30, 18, Equatorial(272.0, 33.0)),
        MeteorShower("eta_aquariids", 4, 19, 5, 6, 5, 28, 50, Equatorial(338.0, -1.0)),
        MeteorShower("delta_aquariids", 7, 12, 7, 30, 8, 23, 25, Equatorial(340.0, -16.0)),
        MeteorShower("perseids", 7, 17, 8, 12, 8, 24, 100, Equatorial(48.0, 58.0)),
        MeteorShower("draconids", 10, 6, 10, 8, 10, 10, 10, Equatorial(262.0, 54.0)),
        MeteorShower("orionids", 10, 2, 10, 21, 11, 7, 20, Equatorial(95.0, 16.0)),
        MeteorShower("leonids", 11, 6, 11, 17, 11, 30, 15, Equatorial(152.0, 22.0)),
        MeteorShower("geminids", 12, 4, 12, 14, 12, 20, 150, Equatorial(112.0, 33.0)),
        MeteorShower("ursids", 12, 17, 12, 22, 12, 26, 10, Equatorial(217.0, 76.0)),
    )

    private fun dayOfYear(m: Int, d: Int) = m * 32 + d

    fun isActive(s: MeteorShower, month: Int, day: Int): Boolean {
        val x = dayOfYear(month, day)
        val a = dayOfYear(s.startMonth, s.startDay); val b = dayOfYear(s.endMonth, s.endDay)
        return if (a <= b) x in a..b else (x >= a || x <= b)
    }
}
