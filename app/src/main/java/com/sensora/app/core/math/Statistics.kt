package com.sensora.app.core.math

import kotlin.math.max
import kotlin.math.sqrt

/** Immutable running min/max/mean. */
data class Stats(
    val count: Int = 0,
    val min: Double = Double.NaN,
    val max: Double = Double.NaN,
    val sum: Double = 0.0,
) {
    val mean: Double get() = if (count == 0) Double.NaN else sum / count

    fun add(v: Double): Stats {
        if (v.isNaN() || v.isInfinite()) return this
        return Stats(
            count = count + 1,
            min = if (count == 0) v else kotlin.math.min(min, v),
            max = if (count == 0) v else kotlin.math.max(max, v),
            sum = sum + v,
        )
    }

    companion object {
        val EMPTY = Stats()
    }
}

/** Fixed-size window with sample standard deviation. */
class RollingWindow(private val capacity: Int) {
    private val buf = ArrayDeque<Double>()

    val size: Int get() = buf.size
    val isFull: Boolean get() = buf.size >= capacity

    fun add(v: Double) {
        buf.addLast(v)
        if (buf.size > capacity) buf.removeFirst()
    }

    fun mean(): Double = if (buf.isEmpty()) Double.NaN else buf.average()

    fun stdDev(): Double {
        if (buf.size < 2) return Double.NaN
        val m = mean()
        return sqrt(buf.sumOf { (it - m) * (it - m) } / (buf.size - 1))
    }

    fun clear() = buf.clear()
}

/** Tracks how steady two angles are; returns NaN until the window is full. */
class StabilityTracker(window: Int = 20) {
    private val a = RollingWindow(window)
    private val b = RollingWindow(window)

    fun add(x: Double, y: Double): Double {
        a.add(x); b.add(y)
        return if (!a.isFull) Double.NaN else max(a.stdDev(), b.stdDev())
    }
}

object Series {
    /** Reduces a series to at most [maxPoints] by averaging consecutive chunks. */
    fun downsample(values: List<Float>, maxPoints: Int): List<Float> {
        if (values.size <= maxPoints || maxPoints <= 0) return values
        val chunk = (values.size + maxPoints - 1) / maxPoints
        return values.chunked(chunk).map { c -> c.sum() / c.size }
    }
}
