package com.sensora.app.core.math

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/** Angles of the device's X axis (roll) and Y axis (pitch) against the horizontal plane. */
data class Tilt(val rollDeg: Double, val pitchDeg: Double)

object OrientationMath {
    fun normalizeDegrees(d: Double): Double {
        val r = d % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Shortest signed difference a − b in (−180, 180]. */
    fun signedDifference(a: Double, b: Double): Double {
        var d = normalizeDegrees(a - b)
        if (d > 180.0) d -= 360.0
        return d
    }

    /** Exponential smoothing that handles the 359° → 0° wrap correctly. */
    fun smoothAngle(prev: Double?, next: Double, alpha: Double): Double =
        if (prev == null) normalizeDegrees(next)
        else normalizeDegrees(prev + alpha * signedDifference(next, prev))

    /** Index 0..15 into the 16-point compass rose (N, NNE, NE, …). */
    fun cardinal16(deg: Double): Int = ((normalizeDegrees(deg) + 11.25) / 22.5).toInt() % 16

    fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    /** Tilt from a gravity/up vector reported by the accelerometer (device coordinates). */
    fun tilt(gx: Double, gy: Double, gz: Double): Tilt = Tilt(
        rollDeg = Math.toDegrees(atan2(gx, sqrt(gy * gy + gz * gz))),
        pitchDeg = Math.toDegrees(atan2(gy, sqrt(gx * gx + gz * gz))),
    )

    /** Angle between the screen plane and the horizontal, 0° when lying flat. */
    fun totalTilt(gx: Double, gy: Double, gz: Double): Double =
        Math.toDegrees(atan2(sqrt(gx * gx + gy * gy), gz))

    /**
     * Deviation from the nearest true vertical/horizontal edge when the phone stands on an edge.
     * Works in portrait and landscape.
     */
    fun verticalDeviation(gx: Double, gy: Double): Double {
        val theta = Math.toDegrees(atan2(gx, gy))
        return theta - (theta / 90.0).roundToInt() * 90.0
    }

    fun slopePercent(deg: Double): Double = tan(Math.toRadians(deg)) * 100.0
    fun slopeMmPerM(deg: Double): Double = tan(Math.toRadians(deg)) * 1000.0
}
