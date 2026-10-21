package com.sensora.app.core.math

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Microphone level math. Values are dBFS (relative to digital full scale), NOT dB SPL.
 * dB SPL is only shown after the user calibrates against a reference meter.
 */
object AudioMath {
    const val FULL_SCALE = 32768.0
    const val MIN_DB = -120.0

    fun rms(buf: ShortArray, n: Int): Double {
        if (n <= 0) return 0.0
        var s = 0.0
        for (i in 0 until n) {
            val v = buf[i].toDouble()
            s += v * v
        }
        return sqrt(s / n)
    }

    fun dbfs(rms: Double): Double = if (rms <= 0.0) MIN_DB else max(MIN_DB, 20.0 * log10(rms / FULL_SCALE))

    fun clippingRatio(buf: ShortArray, n: Int): Double {
        if (n <= 0) return 0.0
        var c = 0
        for (i in 0 until n) if (abs(buf[i].toInt()) >= 32000) c++
        return c.toDouble() / n
    }

    /** Energy (Leq-style) average of decibel values. */
    fun energyAverage(levels: Collection<Double>): Double =
        if (levels.isEmpty()) Double.NaN
        else 10.0 * log10(levels.map { 10.0.pow(it / 10.0) }.average())
}
