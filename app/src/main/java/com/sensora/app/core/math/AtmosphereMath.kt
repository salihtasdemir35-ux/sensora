package com.sensora.app.core.math

import kotlin.math.pow

/** International Standard Atmosphere (troposphere) approximations. */
object AtmosphereMath {
    const val STANDARD_SEA_LEVEL_HPA = 1013.25

    fun altitudeFromPressure(hPa: Double, seaLevelHPa: Double = STANDARD_SEA_LEVEL_HPA): Double =
        44330.0 * (1.0 - (hPa / seaLevelHPa).pow(1.0 / 5.255))

    /** Sea-level reference pressure that makes [hPa] correspond to a known [altitudeM]. */
    fun seaLevelPressure(hPa: Double, altitudeM: Double): Double =
        hPa / (1.0 - altitudeM / 44330.0).pow(5.255)

    fun hPaToInHg(hPa: Double): Double = hPa * 0.029529983071445
    fun hPaToMmHg(hPa: Double): Double = hPa * 0.750061683
}
