package com.sensora.app.core.camera

import com.sensora.app.core.model.Quality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
}

/** Geometry on 3D points placed with AR (metres). */
object MeasureMath {
    fun distance(a: Vec3, b: Vec3): Double = (a - b).length()

    fun pathLength(points: List<Vec3>): Double = points.zipWithNext { a, b -> distance(a, b) }.sum()

    fun perimeter(points: List<Vec3>): Double =
        if (points.size < 3) pathLength(points) else pathLength(points) + distance(points.last(), points.first())

    /** Area of a (nearly) planar polygon in 3D using Newell's method. */
    fun polygonArea(points: List<Vec3>): Double {
        if (points.size < 3) return 0.0
        var n = Vec3(0.0, 0.0, 0.0)
        for (i in points.indices) n += points[i].cross(points[(i + 1) % points.size])
        return n.length() / 2.0
    }
}

enum class HitKind { PLANE, DEPTH, POINT }

object ArQuality {
    /** Explicit rules: detected planes are most reliable, depth points next, feature points least. */
    fun of(tracking: Boolean, kind: HitKind?, distanceM: Double?): Quality = when {
        !tracking || kind == null || distanceM == null -> Quality.UNRELIABLE
        distanceM > 5.0 -> Quality.UNRELIABLE
        kind == HitKind.PLANE && distanceM <= 3.0 -> Quality.GOOD
        kind == HitKind.POINT && distanceM > 2.0 -> Quality.UNRELIABLE
        else -> Quality.LIMITED
    }
}

data class AngleResult(
    val distance: Double?,
    val distanceRange: ClosedFloatingPointRange<Double>?,
    val height: Double?,
    val heightRange: ClosedFloatingPointRange<Double>?,
    val relativeError: Double,
)

/**
 * Height and distance from tilt angles (clinometer method).
 * The camera is at [eyeHeight] above the ground; the base is seen at a depression angle (below horizontal),
 * the top at an elevation angle. Uncertainty is propagated from assumed angle and height errors.
 */
object AngleMeasure {
    private fun tand(d: Double) = tan(d * PI / 180.0)

    /** Elevation of the back camera's optical axis above the horizontal, from a gravity/up vector. */
    fun elevationFromGravity(gx: Double, gy: Double, gz: Double): Double {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        return if (n < 1e-6) 0.0 else asin((-gz / n).coerceIn(-1.0, 1.0)) * 180.0 / PI
    }

    fun distanceFromDepression(eyeHeight: Double, depressionDeg: Double): Double? =
        if (depressionDeg <= 0.5 || depressionDeg >= 89.5) null else eyeHeight / tand(depressionDeg)

    fun heightFromElevation(eyeHeight: Double, distance: Double, elevationDeg: Double): Double =
        eyeHeight + distance * tand(elevationDeg)

    /**
     * @param baseAngleDeg angle to the base (negative = below horizontal); ignored when [knownDistance] is given.
     * @param topAngleDeg angle to the top, or null to measure distance only.
     */
    fun solve(
        eyeHeight: Double,
        baseAngleDeg: Double?,
        topAngleDeg: Double?,
        knownDistance: Double? = null,
        angleErrorDeg: Double = 0.5,
        heightError: Double = 0.05,
    ): AngleResult {
        val d: Double?
        val dRange: ClosedFloatingPointRange<Double>?
        if (knownDistance != null) {
            d = knownDistance
            dRange = knownDistance * 0.99..knownDistance * 1.01
        } else {
            val dep = baseAngleDeg?.let { -it }
            d = dep?.let { distanceFromDepression(eyeHeight, it) }
            dRange = if (d != null && dep != null) {
                val lo = distanceFromDepression(eyeHeight - heightError, dep + angleErrorDeg)
                val hi = distanceFromDepression(eyeHeight + heightError, dep - angleErrorDeg)
                if (lo != null && hi != null) lo..hi else null
            } else null
        }
        if (d == null) return AngleResult(null, null, null, null, Double.POSITIVE_INFINITY)
        if (topAngleDeg == null) {
            val rel = dRange?.let { (it.endInclusive - it.start) / (2 * d) } ?: Double.POSITIVE_INFINITY
            return AngleResult(d, dRange, null, null, rel)
        }
        val h = heightFromElevation(eyeHeight, d, topAngleDeg)
        val hRange = dRange?.let {
            val a = heightFromElevation(eyeHeight - heightError, it.start, topAngleDeg - angleErrorDeg)
            val b = heightFromElevation(eyeHeight + heightError, it.endInclusive, topAngleDeg + angleErrorDeg)
            minOf(a, b)..maxOf(a, b)
        }
        val rel = if (hRange != null && abs(h) > 1e-6) (hRange.endInclusive - hRange.start) / (2 * abs(h)) else Double.POSITIVE_INFINITY
        return AngleResult(d, dRange, h, hRange, rel)
    }

    fun quality(relativeError: Double): Quality = when {
        relativeError.isNaN() || relativeError > 0.15 -> Quality.UNRELIABLE
        relativeError > 0.05 -> Quality.LIMITED
        else -> Quality.GOOD
    }
}

/** Size from a reference object of known length lying in the same plane as the target. */
object ReferenceScale {
    fun length(referenceLength: Double, referencePx: Double, targetPx: Double): Double? =
        if (referencePx < 1.0) null else referenceLength * targetPx / referencePx

    /** Never better than LIMITED: perspective and plane assumptions cannot be verified from one photo. */
    fun quality(referencePx: Double, targetPx: Double): Quality = when {
        referencePx < 60.0 || targetPx < 20.0 -> Quality.UNRELIABLE
        targetPx / referencePx > 8.0 -> Quality.UNRELIABLE
        else -> Quality.LIMITED
    }
}

/** Area and volume formulas for manually entered dimensions (metres). */
object Geometry {
    fun rectangle(a: Double, b: Double) = a * b
    fun circle(r: Double) = PI * r * r
    fun triangle(a: Double, b: Double, c: Double): Double? {
        if (a <= 0 || b <= 0 || c <= 0 || a + b <= c || a + c <= b || b + c <= a) return null
        val s = (a + b + c) / 2
        return sqrt(s * (s - a) * (s - b) * (s - c))
    }
    fun wall(width: Double, height: Double, openings: Double) = (width * height - openings).coerceAtLeast(0.0)
    fun box(a: Double, b: Double, c: Double) = a * b * c
    fun cylinder(r: Double, h: Double) = PI * r * r * h
    fun sphere(r: Double) = 4.0 / 3.0 * PI * r * r * r
    fun cone(r: Double, h: Double) = PI * r * r * h / 3.0
}
