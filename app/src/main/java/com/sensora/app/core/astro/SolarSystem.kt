package com.sensora.app.core.astro

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class Body { SUN, MOON, MERCURY, VENUS, MARS, JUPITER, SATURN, URANUS, NEPTUNE }

/**
 * Geocentric apparent-of-date position (no nutation/aberration; ~1′ for planets, a few ′ for the Moon).
 * [distance] is in AU, except for the Moon where it is in Earth radii.
 */
data class BodyPosition(
    val body: Body,
    val equatorial: Equatorial,
    val distance: Double,
    val eclipticLonDeg: Double,
    val eclipticLatDeg: Double,
    val magnitude: Double,
    val elongationDeg: Double,
    val illuminatedFraction: Double,
)

/** Low-precision ephemeris after Paul Schlyter, "How to compute planetary positions". */
object SolarSystem {
    private class El(val n: Double, val i: Double, val w: Double, val a: Double, val e: Double, val m: Double)

    fun dayNumber(jd: Double) = jd - 2451543.5
    fun obliquity(d: Double) = 23.4393 - 3.563e-7 * d

    private fun elements(body: Body, d: Double): El = when (body) {
        Body.SUN -> El(0.0, 0.0, 282.9404 + 4.70935e-5 * d, 1.0, 0.016709 - 1.151e-9 * d, 356.0470 + 0.9856002585 * d)
        Body.MOON -> El(125.1228 - 0.0529538083 * d, 5.1454, 318.0634 + 0.1643573223 * d, 60.2666, 0.054900, 115.3654 + 13.0649929509 * d)
        Body.MERCURY -> El(48.3313 + 3.24587e-5 * d, 7.0047 + 5.00e-8 * d, 29.1241 + 1.01444e-5 * d, 0.387098, 0.205635 + 5.59e-10 * d, 168.6562 + 4.0923344368 * d)
        Body.VENUS -> El(76.6799 + 2.46590e-5 * d, 3.3946 + 2.75e-8 * d, 54.8910 + 1.38374e-5 * d, 0.723330, 0.006773 - 1.302e-9 * d, 48.0052 + 1.6021302244 * d)
        Body.MARS -> El(49.5574 + 2.11081e-5 * d, 1.8497 - 1.78e-8 * d, 286.5016 + 2.92961e-5 * d, 1.523688, 0.093405 + 2.516e-9 * d, 18.6021 + 0.5240207766 * d)
        Body.JUPITER -> El(100.4542 + 2.76854e-5 * d, 1.3030 - 1.557e-7 * d, 273.8777 + 1.64505e-5 * d, 5.20256, 0.048498 + 4.469e-9 * d, 19.8950 + 0.0830853001 * d)
        Body.SATURN -> El(113.6634 + 2.38980e-5 * d, 2.4886 - 1.081e-7 * d, 339.3939 + 2.97661e-5 * d, 9.55475, 0.055546 - 9.499e-9 * d, 316.9670 + 0.0334442282 * d)
        Body.URANUS -> El(74.0005 + 1.3978e-5 * d, 0.7733 + 1.9e-8 * d, 96.6612 + 3.0565e-5 * d, 19.18171 - 1.55e-8 * d, 0.047318 + 7.45e-9 * d, 142.5905 + 0.011725806 * d)
        Body.NEPTUNE -> El(131.7806 + 3.0173e-5 * d, 1.7700 - 2.55e-7 * d, 272.8461 - 6.027e-6 * d, 30.05826 + 3.313e-8 * d, 0.008606 + 2.15e-9 * d, 260.2471 + 0.005995147 * d)
    }

    private fun eccentricAnomaly(mDeg: Double, e: Double): Double {
        val m = norm360(mDeg)
        var ea = m + Math.toDegrees(e * sind(m) * (1.0 + e * cosd(m)))
        repeat(10) {
            val next = ea - (ea - Math.toDegrees(e * sind(ea)) - m) / (1.0 - e * cosd(ea))
            if (abs(next - ea) < 1e-7) return next
            ea = next
        }
        return ea
    }

    /** Heliocentric (or geocentric for the Moon) ecliptic rectangular coordinates of the orbit. */
    private fun orbit(el: El): DoubleArray {
        val ea = eccentricAnomaly(el.m, el.e)
        val xv = el.a * (cosd(ea) - el.e)
        val yv = el.a * sqrt(1.0 - el.e * el.e) * sind(ea)
        val v = atan2d(yv, xv)
        val r = sqrt(xv * xv + yv * yv)
        val vw = v + el.w
        return doubleArrayOf(
            r * (cosd(el.n) * cosd(vw) - sind(el.n) * sind(vw) * cosd(el.i)),
            r * (sind(el.n) * cosd(vw) + cosd(el.n) * sind(vw) * cosd(el.i)),
            r * sind(vw) * sind(el.i),
            r,
        )
    }

    private fun sphericalToRect(lon: Double, lat: Double, r: Double) =
        doubleArrayOf(r * cosd(lon) * cosd(lat), r * sind(lon) * cosd(lat), r * sind(lat))

    fun position(body: Body, jd: Double): BodyPosition {
        val d = dayNumber(jd)
        val ecl = obliquity(d)
        val sunEl = elements(Body.SUN, d)
        val sun = orbit(sunEl)
        val xs = sun[0]; val ys = sun[1]; val rs = sun[3]
        val sunLon = norm360(atan2d(ys, xs))

        var helioR = 0.0
        val g: DoubleArray = when (body) {
            Body.SUN -> doubleArrayOf(xs, ys, 0.0)
            Body.MOON -> moonGeocentric(d, sunEl)
            else -> {
                val h = planetHeliocentric(body, d)
                helioR = sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2])
                doubleArrayOf(h[0] + xs, h[1] + ys, h[2])
            }
        }
        val dist = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        val lon = norm360(atan2d(g[1], g[0]))
        val lat = atan2d(g[2], sqrt(g[0] * g[0] + g[1] * g[1]))

        val xe = g[0]
        val ye = g[1] * cosd(ecl) - g[2] * sind(ecl)
        val ze = g[1] * sind(ecl) + g[2] * cosd(ecl)
        val eq = Equatorial(norm360(atan2d(ye, xe)), atan2d(ze, sqrt(xe * xe + ye * ye)))

        val elong = if (body == Body.SUN) 0.0 else acosd(cosd(lon - sunLon) * cosd(lat))
        val (mag, illum) = when (body) {
            Body.SUN -> -26.74 to 1.0
            Body.MOON -> {
                val fv = 180.0 - elong
                (-12.73 + 0.026 * abs(fv) + 4e-9 * fv.pow(4)) to (1.0 - cosd(elong)) / 2.0
            }
            else -> {
                val fv = acosd((helioR * helioR + dist * dist - rs * rs) / (2 * helioR * dist))
                planetMagnitude(body, helioR, dist, fv, lon, lat, d) to (1.0 + cosd(fv)) / 2.0
            }
        }
        return BodyPosition(body, eq, dist, lon, lat, mag, elong, illum)
    }

    private fun moonGeocentric(d: Double, sunEl: El): DoubleArray {
        val el = elements(Body.MOON, d)
        val o = orbit(el)
        var lon = atan2d(o[1], o[0])
        var lat = atan2d(o[2], sqrt(o[0] * o[0] + o[1] * o[1]))
        var r = o[3]
        val ms = sunEl.m; val mm = el.m
        val ls = sunEl.m + sunEl.w
        val lm = el.m + el.w + el.n
        val dd = lm - ls
        val f = lm - el.n
        lon += -1.274 * sind(mm - 2 * dd) + 0.658 * sind(2 * dd) - 0.186 * sind(ms) -
            0.059 * sind(2 * mm - 2 * dd) - 0.057 * sind(mm - 2 * dd + ms) + 0.053 * sind(mm + 2 * dd) +
            0.046 * sind(2 * dd - ms) + 0.041 * sind(mm - ms) - 0.035 * sind(dd) - 0.031 * sind(mm + ms) -
            0.015 * sind(2 * f - 2 * dd) + 0.011 * sind(mm - 4 * dd)
        lat += -0.173 * sind(f - 2 * dd) - 0.055 * sind(mm - f - 2 * dd) - 0.046 * sind(mm + f - 2 * dd) +
            0.033 * sind(f + 2 * dd) + 0.017 * sind(2 * mm + f)
        r += -0.58 * cosd(mm - 2 * dd) - 0.46 * cosd(2 * dd)
        return sphericalToRect(lon, lat, r)
    }

    private fun planetHeliocentric(body: Body, d: Double): DoubleArray {
        val o = orbit(elements(body, d))
        if (body != Body.JUPITER && body != Body.SATURN && body != Body.URANUS) return o
        var lon = atan2d(o[1], o[0])
        var lat = atan2d(o[2], sqrt(o[0] * o[0] + o[1] * o[1]))
        val mj = elements(Body.JUPITER, d).m
        val msa = elements(Body.SATURN, d).m
        val mu = elements(Body.URANUS, d).m
        when (body) {
            Body.JUPITER -> lon += -0.332 * sind(2 * mj - 5 * msa - 67.6) - 0.056 * sind(2 * mj - 2 * msa + 21) +
                0.042 * sind(3 * mj - 5 * msa + 21) - 0.036 * sind(mj - 2 * msa) + 0.022 * cosd(mj - msa) +
                0.023 * sind(2 * mj - 3 * msa + 52) - 0.016 * sind(mj - 5 * msa - 69)
            Body.SATURN -> {
                lon += 0.812 * sind(2 * mj - 5 * msa - 67.6) - 0.229 * cosd(2 * mj - 4 * msa - 2) +
                    0.119 * sind(mj - 2 * msa - 3) + 0.046 * sind(2 * mj - 6 * msa - 69) + 0.014 * sind(mj - 3 * msa + 32)
                lat += -0.020 * cosd(2 * mj - 4 * msa - 2) + 0.018 * sind(2 * mj - 6 * msa - 49)
            }
            else -> lon += 0.040 * sind(msa - 2 * mu + 6) + 0.035 * sind(msa - 3 * mu + 33) - 0.015 * sind(mj - mu + 20)
        }
        return sphericalToRect(lon, lat, o[3])
    }

    private fun planetMagnitude(body: Body, r: Double, rr: Double, fv: Double, lon: Double, lat: Double, d: Double): Double {
        val base = 5 * log10(r * rr)
        return when (body) {
            Body.MERCURY -> -0.36 + base + 0.027 * fv + 2.2e-13 * fv.pow(6)
            Body.VENUS -> -4.34 + base + 0.013 * fv + 4.2e-7 * fv.pow(3)
            Body.MARS -> -1.51 + base + 0.016 * fv
            Body.JUPITER -> -9.25 + base + 0.014 * fv
            Body.SATURN -> {
                val ir = 28.06
                val nr = 169.51 + 3.82e-5 * d
                val b = asind(sind(lat) * cosd(ir) - cosd(lat) * sind(ir) * sind(lon - nr))
                -9.0 + base + 0.044 * fv - 2.6 * sind(abs(b)) + 1.2 * sind(b) * sind(b)
            }
            Body.URANUS -> -7.15 + base + 0.001 * fv
            else -> -6.90 + base + 0.001 * fv
        }
    }

    /** Topocentric correction of the Moon's altitude for parallax (about 1° at the horizon). */
    fun moonParallaxDeg(distanceEarthRadii: Double): Double = Math.toDegrees(asin(1.0 / distanceEarthRadii))
}
