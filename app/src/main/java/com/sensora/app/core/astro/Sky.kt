package com.sensora.app.core.astro

/** Altitude/azimuth helpers that hide time and precession bookkeeping from the UI. */
object Sky {
    data class BodyNow(val position: BodyPosition, val horizontal: Horizontal)

    fun body(body: Body, obs: Observer, epochMs: Long): BodyNow {
        val jd = AstroTime.julianDay(epochMs)
        val pos = SolarSystem.position(body, jd)
        var h = Coords.toHorizontal(pos.equatorial, obs.latDeg, AstroTime.lstDeg(jd, obs.lonDeg))
        if (body == Body.MOON) {
            h = h.copy(altDeg = h.altDeg - SolarSystem.moonParallaxDeg(pos.distance) * cosd(h.altDeg))
        }
        return BodyNow(pos, h)
    }

    fun fixed(j2000: Equatorial, obs: Observer, epochMs: Long): Horizontal {
        val jd = AstroTime.julianDay(epochMs)
        return Coords.toHorizontal(Coords.precessFromJ2000(j2000, jd), obs.latDeg, AstroTime.lstDeg(jd, obs.lonDeg))
    }

    /** Fast altitude function for a fixed object: precession computed once for the given epoch. */
    fun fixedAltitude(j2000: Equatorial, obs: Observer, referenceEpochMs: Long): (Long) -> Double {
        val eq = Coords.precessFromJ2000(j2000, AstroTime.julianDay(referenceEpochMs))
        return { t -> Coords.toHorizontal(eq, obs.latDeg, AstroTime.lstDeg(AstroTime.julianDay(t), obs.lonDeg)).altDeg }
    }

    fun bodyAltitude(body: Body, obs: Observer): (Long) -> Double = { t -> body(body, obs, t).horizontal.altDeg }
}

data class RiseSetEvents(
    val rise: Long?,
    val transit: Long?,
    val set: Long?,
    val transitAltDeg: Double,
    val alwaysUp: Boolean,
    val alwaysDown: Boolean,
)

data class Crossing(val timeMs: Long, val rising: Boolean)

object RiseSet {
    const val H0_SUN = -0.833
    const val H0_MOON = -0.833   // applied to topocentric altitude: refraction + semidiameter
    const val H0_STAR = -0.567
    private const val STEP_MS = 10 * 60 * 1000L

    fun crossings(startMs: Long, endMs: Long, h0: Double, alt: (Long) -> Double, stepMs: Long = STEP_MS): List<Crossing> {
        val out = ArrayList<Crossing>()
        var t0 = startMs
        var a0 = alt(t0) - h0
        while (t0 < endMs) {
            val t1 = minOf(t0 + stepMs, endMs)
            val a1 = alt(t1) - h0
            if ((a0 < 0) != (a1 < 0)) {
                var lo = t0; var hi = t1; var flo = a0
                repeat(18) {
                    val mid = (lo + hi) / 2
                    val fm = alt(mid) - h0
                    if ((fm < 0) == (flo < 0)) { lo = mid; flo = fm } else hi = mid
                }
                out.add(Crossing((lo + hi) / 2, a1 > a0))
            }
            t0 = t1; a0 = a1
        }
        return out
    }

    fun events(startMs: Long, endMs: Long, h0: Double, alt: (Long) -> Double): RiseSetEvents {
        val cs = crossings(startMs, endMs, h0, alt)
        var bestT = startMs; var bestA = -90.0
        var t = startMs
        while (t <= endMs) {
            val a = alt(t)
            if (a > bestA) { bestA = a; bestT = t }
            t += STEP_MS
        }
        // Refine the transit with a ternary search around the best sample.
        var lo = maxOf(startMs, bestT - STEP_MS); var hi = minOf(endMs, bestT + STEP_MS)
        repeat(30) {
            val m1 = lo + (hi - lo) / 3; val m2 = hi - (hi - lo) / 3
            if (alt(m1) < alt(m2)) lo = m1 else hi = m2
        }
        val transit = (lo + hi) / 2
        val transitAlt = alt(transit)
        val anyAbove = transitAlt > h0
        return RiseSetEvents(
            rise = cs.firstOrNull { it.rising }?.timeMs,
            transit = if (anyAbove) transit else null,
            set = cs.firstOrNull { !it.rising }?.timeMs,
            transitAltDeg = transitAlt,
            alwaysUp = cs.isEmpty() && alt(startMs) > h0,
            alwaysDown = cs.isEmpty() && !anyAbove,
        )
    }
}
