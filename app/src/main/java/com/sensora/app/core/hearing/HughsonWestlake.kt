package com.sensora.app.core.hearing

import kotlin.math.abs

/**
 * Modified Hughson–Westlake threshold search (ASHA 2005, ISO 8253-1): "down 10, up 5".
 * Threshold = lowest level heard on at least 2 ascending presentations.
 * Levels are in dB relative to full scale (dBFS) because phone output is not calibrated.
 */
class ThresholdSearch(
    start: Int = -40,
    private val minLevel: Int = -95,
    private val maxLevel: Int = -5,
    private val maxPresentations: Int = 30,
) {
    var level = start; private set
    var done = false; private set
    /** Null when no response at the maximum level. */
    var threshold: Int? = null; private set
    var presentations = 0; private set

    private var phase = Phase.SEEK
    private val heardOnAscent = HashMap<Int, Int>()

    private enum class Phase { SEEK, DESCEND, ASCEND }

    fun respond(heard: Boolean) {
        if (done) return
        presentations++
        when (phase) {
            Phase.SEEK -> if (heard) { phase = Phase.DESCEND; down() } else up(20)
            Phase.DESCEND -> if (heard) down() else { phase = Phase.ASCEND; up(5) }
            Phase.ASCEND -> if (heard) {
                val n = (heardOnAscent[level] ?: 0) + 1
                heardOnAscent[level] = n
                if (n >= 2) finish(level) else { phase = Phase.DESCEND; down() }
            } else up(5)
        }
        if (!done && presentations >= maxPresentations) finish(heardOnAscent.keys.minOrNull())
    }

    private fun down() {
        if (level - 10 < minLevel) { finish(minLevel); return }
        level -= 10
    }

    private fun up(step: Int) {
        if (level >= maxLevel) { finish(null); return }
        level = minOf(maxLevel, level + step)
    }

    private fun finish(t: Int?) { threshold = t; done = true }
}

enum class Ear { RIGHT, LEFT }

data class EarResult(val ear: Ear, val frequency: Int, val threshold: Int?)

data class HearingScreeningResult(
    val results: List<EarResult>,
    val retestDifference: Int?,
    val falseAlarmRate: Double,
    val asymmetric: Boolean,
    val asymmetricFrequencies: List<Int>,
    val reliable: Boolean,
)

object HearingScreening {
    val FREQUENCIES = listOf(1000, 2000, 4000, 8000, 500, 250)
    val ORDERED = listOf(250, 500, 1000, 2000, 4000, 8000)

    /**
     * Because both ears are tested with the same headphones and volume, the difference between ears
     * is meaningful even without calibration: ≥15 dB at two adjacent frequencies, ≥20 dB at one,
     * or a "no response" in one ear only.
     */
    fun evaluate(results: List<EarResult>, retest: Pair<Int?, Int?>, catchTrials: Int, falseAlarms: Int): HearingScreeningResult {
        fun t(ear: Ear, f: Int) = results.firstOrNull { it.ear == ear && it.frequency == f }
        val diffs = ORDERED.map { f ->
            val r = t(Ear.RIGHT, f); val l = t(Ear.LEFT, f)
            when {
                r == null || l == null -> null
                r.threshold == null && l.threshold == null -> 0
                r.threshold == null || l.threshold == null -> 100
                else -> abs(r.threshold - l.threshold)
            }
        }
        val flagged = ORDERED.indices.filter { i ->
            val d = diffs[i] ?: return@filter false
            d >= 20 || (d >= 15 && ((i > 0 && (diffs[i - 1] ?: 0) >= 15) || (i < diffs.size - 1 && (diffs[i + 1] ?: 0) >= 15)))
        }.map { ORDERED[it] }
        val retestDiff = if (retest.first != null && retest.second != null) abs(retest.first!! - retest.second!!) else null
        val faRate = if (catchTrials > 0) falseAlarms.toDouble() / catchTrials else 0.0
        val reliable = (retestDiff == null || retestDiff <= 10) && faRate <= 0.25
        return HearingScreeningResult(results, retestDiff, faRate, flagged.isNotEmpty(), flagged, reliable)
    }
}
