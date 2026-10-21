package com.sensora.app.core.math

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timeMs: Long,
    val speedMps: Float? = null,
    val altitudeM: Double? = null,
)

/** Accumulates distance, speed and ascent from GPS fixes while filtering jitter and bad fixes. */
class TrackAccumulator(private val maxAccuracyM: Float = 25f) {
    var distanceM = 0.0; private set
    var maxSpeedMps = 0.0; private set
    var ascentM = 0.0; private set
    var currentSpeedMps: Double? = null; private set
    var acceptedFixes = 0; private set

    private var lastAccepted: TrackPoint? = null
    private var altRef: Double? = null

    fun add(p: TrackPoint) {
        if (p.accuracyM > maxAccuracyM) return
        val prev = lastAccepted
        val speed = p.speedMps?.toDouble() ?: prev?.let {
            val dt = (p.timeMs - it.timeMs) / 1000.0
            if (dt > 0) GeoMath.haversine(it.lat, it.lon, p.lat, p.lon) / dt else null
        }
        currentSpeedMps = speed
        if (speed != null && speed > maxSpeedMps && speed < 120.0) maxSpeedMps = speed
        acceptedFixes++

        p.altitudeM?.let { alt ->
            val ref = altRef
            when {
                ref == null -> altRef = alt
                alt - ref > 3.0 -> { ascentM += alt - ref; altRef = alt }
                ref - alt > 3.0 -> altRef = alt
            }
        }

        if (prev == null) { lastAccepted = p; return }
        val d = GeoMath.haversine(prev.lat, prev.lon, p.lat, p.lon)
        // Movement smaller than the fixes' uncertainty is treated as jitter, not distance.
        if (d >= max(3.0, (prev.accuracyM + p.accuracyM) / 2.0)) {
            distanceM += d
            lastAccepted = p
        }
    }

    fun averageSpeedMps(elapsedMs: Long): Double? = if (elapsedMs > 0) distanceM / (elapsedMs / 1000.0) else null
}
