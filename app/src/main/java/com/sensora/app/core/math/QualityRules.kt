package com.sensora.app.core.math

import com.sensora.app.core.model.Quality

/** Explicit, testable quality rules for every measurement module. */
object QualityRules {
    // Mirrors android.hardware.SensorManager.SENSOR_STATUS_* so this file stays JVM-testable.
    const val STATUS_UNRELIABLE = 0
    const val STATUS_LOW = 1
    const val STATUS_MEDIUM = 2
    const val STATUS_HIGH = 3

    fun fromSensorAccuracy(status: Int): Quality = when (status) {
        STATUS_HIGH -> Quality.GOOD
        STATUS_MEDIUM -> Quality.LIMITED
        else -> Quality.UNRELIABLE
    }

    /** Earth's magnetic field is roughly 25–65 µT at the surface. */
    fun magneticField(microTesla: Double): Quality = when {
        microTesla.isNaN() -> Quality.UNRELIABLE
        microTesla in 22.0..70.0 -> Quality.GOOD
        microTesla in 15.0..100.0 -> Quality.LIMITED
        else -> Quality.UNRELIABLE
    }

    fun fieldInterference(microTesla: Double): Boolean = magneticField(microTesla) != Quality.GOOD

    fun compass(status: Int, microTesla: Double): Quality =
        Quality.worst(fromSensorAccuracy(status), magneticField(microTesla))

    /** Stability of an angle reading, from its rolling standard deviation in degrees. */
    fun stability(stdDevDeg: Double): Quality = when {
        stdDevDeg.isNaN() -> Quality.UNRELIABLE
        stdDevDeg < 0.15 -> Quality.GOOD
        stdDevDeg < 0.6 -> Quality.LIMITED
        else -> Quality.UNRELIABLE
    }

    /** GPS fix quality from horizontal accuracy (metres, 68 %) and fix age. */
    fun gps(accuracyM: Float?, ageMs: Long): Quality = when {
        accuracyM == null || accuracyM.isNaN() || ageMs > 10_000 -> Quality.UNRELIABLE
        accuracyM <= 10f -> Quality.GOOD
        accuracyM <= 30f -> Quality.LIMITED
        else -> Quality.UNRELIABLE
    }

    /** An uncalibrated phone microphone can never be better than LIMITED. */
    fun soundMeter(calibrated: Boolean, clipping: Boolean): Quality = when {
        clipping -> Quality.UNRELIABLE
        calibrated -> Quality.GOOD
        else -> Quality.LIMITED
    }

    /** A light sensor at its maximum range is saturated: the true value may be higher. */
    fun light(lux: Double, maxRange: Double): Quality = when {
        lux.isNaN() || lux < 0 -> Quality.UNRELIABLE
        maxRange > 0 && lux >= maxRange * 0.98 -> Quality.LIMITED
        else -> Quality.GOOD
    }

    /** Barometric altitude drifts with weather; a recent known-altitude calibration is required for GOOD. */
    fun barometricAltitude(calibrationAgeMs: Long?): Quality = when {
        calibrationAgeMs == null -> Quality.LIMITED
        calibrationAgeMs <= 3 * 3_600_000L -> Quality.GOOD
        else -> Quality.LIMITED
    }
}
