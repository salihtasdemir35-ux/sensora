package com.sensora.app.core.astro

/** Sun events for one civil day [dayStartMs, dayStartMs + 24 h). */
data class SunDay(
    val events: RiseSetEvents,
    private val rising: Map<Double, Long?>,
    private val setting: Map<Double, Long?>,
) {
    fun risingAt(h: Double): Long? = rising[h]
    fun settingAt(h: Double): Long? = setting[h]
    val dayLengthMs: Long?
        get() = when {
            events.alwaysUp -> AstroTime.DAY_MS
            events.alwaysDown -> 0L
            events.rise != null && events.set != null && events.set > events.rise -> events.set - events.rise
            else -> null
        }

    companion object {
        const val CIVIL = -6.0
        const val NAUTICAL = -12.0
        const val ASTRONOMICAL = -18.0
        const val BLUE_GOLDEN = -4.0
        const val GOLDEN_TOP = 6.0
        val LEVELS = listOf(RiseSet.H0_SUN, CIVIL, NAUTICAL, ASTRONOMICAL, BLUE_GOLDEN, GOLDEN_TOP)
    }
}

object SunTimes {
    fun compute(obs: Observer, dayStartMs: Long): SunDay {
        val end = dayStartMs + AstroTime.DAY_MS
        val alt = Sky.bodyAltitude(Body.SUN, obs)
        val events = RiseSet.events(dayStartMs, end, RiseSet.H0_SUN, alt)
        val rising = HashMap<Double, Long?>(); val setting = HashMap<Double, Long?>()
        for (h in SunDay.LEVELS) {
            val cs = RiseSet.crossings(dayStartMs, end, h, alt)
            rising[h] = cs.firstOrNull { it.rising }?.timeMs
            setting[h] = cs.firstOrNull { !it.rising }?.timeMs
        }
        return SunDay(events, rising, setting)
    }
}

data class MoonPhase(
    val illuminated: Double,
    val elongationDeg: Double,
    val waxing: Boolean,
    /** 0 new, 1 waxing crescent, 2 first quarter, 3 waxing gibbous, 4 full, 5 waning gibbous, 6 last quarter, 7 waning crescent */
    val phaseIndex: Int,
    val ageDays: Double,
)

object MoonInfo {
    const val SYNODIC_DAYS = 29.530588

    private fun lonDiff(epochMs: Long): Double {
        val jd = AstroTime.julianDay(epochMs)
        return norm360(SolarSystem.position(Body.MOON, jd).eclipticLonDeg - SolarSystem.position(Body.SUN, jd).eclipticLonDeg)
    }

    fun phase(epochMs: Long): MoonPhase {
        val jd = AstroTime.julianDay(epochMs)
        val moon = SolarSystem.position(Body.MOON, jd)
        val diff = lonDiff(epochMs)
        return MoonPhase(
            illuminated = moon.illuminatedFraction,
            elongationDeg = moon.elongationDeg,
            waxing = diff < 180.0,
            phaseIndex = (((diff + 22.5) / 45.0).toInt()) % 8,
            ageDays = diff / 360.0 * SYNODIC_DAYS,
        )
    }

    /** Next time after [startMs] when the Moon–Sun longitude difference reaches [targetDeg] (0 new, 180 full). */
    fun next(startMs: Long, targetDeg: Double): Long {
        fun f(t: Long): Double { var x = lonDiff(t) - targetDeg; if (x > 180) x -= 360; if (x < -180) x += 360; return x }
        val step = 6 * 3_600_000L
        var t0 = startMs; var f0 = f(t0)
        repeat(130) {
            val t1 = t0 + step
            val f1 = f(t1)
            if (f0 < 0 && f1 >= 0 && f1 - f0 < 90) {
                var lo = t0; var hi = t1
                repeat(25) { val mid = (lo + hi) / 2; if (f(mid) < 0) lo = mid else hi = mid }
                return (lo + hi) / 2
            }
            t0 = t1; f0 = f1
        }
        return startMs
    }
}
