package com.sensora.app.core.sleep

import com.sensora.app.core.health.Urgency
import kotlin.math.max
import kotlin.math.sqrt

/** One minute of an overnight recording. Audio is reduced to levels; nothing is recorded. */
data class SleepEpoch(val movement: Int, val avgDb: Double, val maxDb: Double, val snore: Boolean, val loudEvents: Int)

/** Finds short sound bursts (100 ms frames) relative to an adaptive noise floor. */
class SoundEventDetector(private val frameSec: Double = 0.1) {
    data class Event(val startSec: Double, val durationSec: Double, val peakAboveFloor: Double)

    private val history = ArrayDeque<Double>()
    private var floor = -70.0
    private var t = 0.0
    private var inEvent = false
    private var start = 0.0
    private var peak = -200.0
    private var below = 0
    private var sinceFloor = 0

    fun add(db: Double): Event? {
        history.addLast(db); if (history.size > 600) history.removeFirst()
        if (++sinceFloor >= 50 && history.size >= 50) { floor = history.sorted()[history.size / 5]; sinceFloor = 0 }
        var out: Event? = null
        if (!inEvent) {
            if (db > floor + 12) { inEvent = true; start = t; peak = db; below = 0 }
        } else {
            if (db > peak) peak = db
            if (db < floor + 8) {
                below++
                if (below >= 2) { inEvent = false; out = Event(start, t - start - (below - 1) * frameSec, peak - floor) }
            } else below = 0
        }
        t += frameSec
        return out
    }

    val noiseFloor: Double get() = floor
}

object SleepMath {
    /** Snoring is rhythmic: several 0.3–3 s sounds, 2–10 s apart, at fairly regular intervals. */
    fun isSnoreMinute(events: List<SoundEventDetector.Event>): Boolean {
        val e = events.filter { it.durationSec in 0.3..3.0 }
        if (e.size < 4) return false
        val gaps = e.zipWithNext { a, b -> b.startSec - a.startSec }.filter { it in 2.0..10.0 }
        if (gaps.size < 3) return false
        val m = gaps.average()
        val cv = sqrt(gaps.sumOf { (it - m) * (it - m) } / gaps.size) / m
        return cv < 0.4
    }
}

enum class AgeGroup(val minHours: Double, val maxHours: Double) { TEEN(8.0, 10.0), ADULT(7.0, 9.0), OLDER(7.0, 8.0) }

enum class SleepFlag { SHORT, LONG, LOW_EFFICIENCY, LONG_ONSET, FREQUENT_SNORING, NOISY_ROOM }

data class SleepReport(
    val timeInBedMin: Int,
    val onsetLatencyMin: Int?,
    val totalSleepMin: Int,
    val efficiency: Double,
    val wakeBouts: Int,
    val snoreMin: Int,
    val loudEvents: Int,
    val active: BooleanArray,
    val flags: Set<SleepFlag>,
    val urgency: Urgency,
)

/**
 * Actigraphy-style estimate: minutes with little movement after sleep onset count as sleep.
 * Thresholds adapt to how the phone is coupled to the mattress (median-based).
 */
object SleepAnalysis {
    fun activeMask(epochs: List<SleepEpoch>): BooleanArray {
        if (epochs.isEmpty()) return BooleanArray(0)
        val med = epochs.map { it.movement }.sorted()[epochs.size / 2]
        val thr = max(10, 4 * med)
        return BooleanArray(epochs.size) { epochs[it].movement > thr }
    }

    fun report(epochs: List<SleepEpoch>, age: AgeGroup): SleepReport {
        val n = epochs.size
        val active = activeMask(epochs)
        var onset: Int? = null
        for (i in 0..n - 10) if ((i until i + 10).none { active[it] }) { onset = i; break }
        val lastSleep = (n - 1 downTo 0).firstOrNull { !active[it] }
        var tst = 0; var bouts = 0
        if (onset != null && lastSleep != null && lastSleep >= onset) {
            var run = 0
            for (i in onset..lastSleep) {
                if (active[i]) run++ else { if (run >= 5) bouts++; run = 0; tst++ }
            }
        }
        val snore = epochs.count { it.snore }
        val loud = epochs.sumOf { it.loudEvents }
        val eff = if (n > 0) tst.toDouble() / n else 0.0
        val flags = mutableSetOf<SleepFlag>()
        if (tst < age.minHours * 60) flags += SleepFlag.SHORT
        if (tst > age.maxHours * 60) flags += SleepFlag.LONG
        if (n >= 60 && eff < 0.85) flags += SleepFlag.LOW_EFFICIENCY
        if (onset != null && onset > 30) flags += SleepFlag.LONG_ONSET
        if (n > 0 && snore.toDouble() / n >= 0.10) flags += SleepFlag.FREQUENT_SNORING
        if (n > 0 && loud.toDouble() / n * 60 >= 6) flags += SleepFlag.NOISY_ROOM
        val urgency = if (SleepFlag.FREQUENT_SNORING in flags || flags.size >= 2) Urgency.DISCUSS else Urgency.NORMAL
        return SleepReport(n, onset, tst, eff, bouts, snore, loud, active, flags, urgency)
    }
}
