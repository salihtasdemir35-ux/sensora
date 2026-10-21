package com.sensora.app.core.steps

import kotlin.math.max

/**
 * Android's TYPE_STEP_COUNTER is cumulative since boot. This tracker turns it into
 * per-day totals and survives device reboots (counter reset to 0).
 */
data class StepDayState(
    val day: String? = null,
    val baseline: Long = 0,
    val lastCounter: Long = -1,
    val carry: Long = 0,
)

data class StepUpdate(val state: StepDayState, val todaySteps: Long, val closedDay: Pair<String, Long>?)

object StepDayTracker {
    fun update(prev: StepDayState, counter: Long, today: String): StepUpdate {
        var s = prev
        var closed: Pair<String, Long>? = null
        if (s.day != today) {
            if (s.day != null && s.lastCounter >= 0) {
                closed = s.day!! to max(0, s.lastCounter - s.baseline + s.carry)
            }
            val continuous = s.lastCounter in 0..counter
            s = StepDayState(
                day = today,
                baseline = if (continuous) s.lastCounter else counter,
                lastCounter = if (continuous) s.lastCounter else -1,
                carry = 0,
            )
        }
        if (s.lastCounter >= 0 && counter < s.lastCounter) {
            // Counter restarted (reboot): keep what was counted today, restart baseline at 0.
            s = s.copy(carry = s.carry + max(0, s.lastCounter - s.baseline), baseline = 0)
        }
        s = s.copy(lastCounter = counter)
        return StepUpdate(s, max(0, counter - s.baseline + s.carry), closed)
    }

    /** Common anthropometric estimate: step length ≈ 0.415 × height. */
    fun stepLengthM(heightCm: Int): Double = heightCm * 0.415 / 100.0
}

/** Fallback detector on raw accelerometer magnitude, used only when no step sensor exists. */
class AccelStepDetector(
    private val threshold: Double = 11.0,
    private val hysteresis: Double = 1.0,
    private val minIntervalMs: Long = 250,
) {
    var steps = 0L; private set
    private var above = false
    private var lastStepMs = Long.MIN_VALUE / 2

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        val m = kotlin.math.sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        if (!above && m > threshold && timeMs - lastStepMs >= minIntervalMs) {
            above = true
            lastStepMs = timeMs
            steps++
            return true
        }
        if (above && m < threshold - hysteresis) above = false
        return false
    }
}
