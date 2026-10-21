package com.sensora.app.core.astro

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal fun sind(d: Double) = sin(Math.toRadians(d))
internal fun cosd(d: Double) = cos(Math.toRadians(d))
internal fun atan2d(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
internal fun asind(x: Double) = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
internal fun acosd(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
internal fun norm360(d: Double): Double { val r = d % 360.0; return if (r < 0) r + 360.0 else r }

data class Equatorial(val raDeg: Double, val decDeg: Double)
data class Horizontal(val azDeg: Double, val altDeg: Double)
data class Observer(val latDeg: Double, val lonDeg: Double, val elevationM: Double = 0.0)

object AstroTime {
    const val J2000 = 2451545.0
    const val DAY_MS = 86_400_000L

    fun julianDay(epochMs: Long): Double = epochMs / 86_400_000.0 + 2440587.5
    fun epochMs(jd: Double): Long = ((jd - 2440587.5) * 86_400_000.0).roundToLong()
    fun centuries(jd: Double): Double = (jd - J2000) / 36525.0

    /** Greenwich mean sidereal time (Meeus 12.4), degrees. */
    fun gmstDeg(jd: Double): Double {
        val t = centuries(jd)
        return norm360(280.46061837 + 360.98564736629 * (jd - J2000) + 0.000387933 * t * t - t * t * t / 38_710_000.0)
    }

    fun lstDeg(jd: Double, lonDeg: Double): Double = norm360(gmstDeg(jd) + lonDeg)
}

object Coords {
    /** Azimuth measured from north through east. */
    fun toHorizontal(eq: Equatorial, latDeg: Double, lstDeg: Double): Horizontal {
        val h = lstDeg - eq.raDeg
        val alt = asind(sind(latDeg) * sind(eq.decDeg) + cosd(latDeg) * cosd(eq.decDeg) * cosd(h))
        val az = atan2d(-cosd(eq.decDeg) * sind(h), sind(eq.decDeg) * cosd(latDeg) - cosd(eq.decDeg) * sind(latDeg) * cosd(h))
        return Horizontal(norm360(az), alt)
    }

    /** Rigorous precession from J2000.0 to the equinox of [jd] (Meeus 21.2–21.4). */
    fun precessFromJ2000(eq: Equatorial, jd: Double): Equatorial {
        val t = AstroTime.centuries(jd)
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0
        val a = cosd(eq.decDeg) * sind(eq.raDeg + zeta)
        val b = cosd(theta) * cosd(eq.decDeg) * cosd(eq.raDeg + zeta) - sind(theta) * sind(eq.decDeg)
        val c = sind(theta) * cosd(eq.decDeg) * cosd(eq.raDeg + zeta) + cosd(theta) * sind(eq.decDeg)
        return Equatorial(norm360(atan2d(a, b) + z), asind(c))
    }

    /** Atmospheric refraction (Bennett), degrees to add to a true altitude. */
    fun refractionDeg(altDeg: Double): Double =
        if (altDeg < -1.0) 0.0 else 1.0 / tan(Math.toRadians(altDeg + 7.31 / (altDeg + 4.4))) / 60.0

    fun separationDeg(a: Equatorial, b: Equatorial): Double =
        acosd(sind(a.decDeg) * sind(b.decDeg) + cosd(a.decDeg) * cosd(b.decDeg) * cosd(a.raDeg - b.raDeg))

    fun unitVector(eq: Equatorial): DoubleArray =
        doubleArrayOf(cosd(eq.decDeg) * cosd(eq.raDeg), cosd(eq.decDeg) * sind(eq.raDeg), sind(eq.decDeg))
}

/**
 * Rotation from equatorial unit vectors (of date) to the local horizon frame (east, north, up).
 * Built once per time step, then applied to thousands of stars cheaply.
 */
class HorizonFrame(latDeg: Double, lstDeg: Double) {
    private val m: DoubleArray

    init {
        val sl = sind(lstDeg); val cl = cosd(lstDeg); val sp = sind(latDeg); val cp = cosd(latDeg)
        m = doubleArrayOf(
            -sl, cl, 0.0,                 // east
            -sp * cl, -sp * sl, cp,       // north
            cp * cl, cp * sl, sp,         // up
        )
    }

    fun toHorizon(x: Double, y: Double, z: Double, out: DoubleArray) {
        out[0] = m[0] * x + m[1] * y + m[2] * z
        out[1] = m[3] * x + m[4] * y + m[5] * z
        out[2] = m[6] * x + m[7] * y + m[8] * z
    }

    companion object {
        fun azAlt(v: DoubleArray): Horizontal =
            Horizontal(norm360(atan2d(v[0], v[1])), asind(v[2] / sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])))
    }
}
