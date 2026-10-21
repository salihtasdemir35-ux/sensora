package com.sensora.app.core.health

/**
 * Microphone "blow test" following the procedure of ATS/ERS 2019 spirometry: explosive start,
 * continuous blow without interruption, minimum duration and repeatability over ≥3 trials.
 * It measures how long and how strongly sound is produced, NOT lung volumes or flow.
 */
enum class BlowIssue { NO_BLOW, SLOW_START, TOO_SHORT, INTERRUPTED, CLIPPED }

data class BlowTrial(
    val durationSec: Double,
    val peakAboveFloorDb: Double,
    val timeToPeakSec: Double,
    val issues: Set<BlowIssue>,
) {
    val acceptable: Boolean get() = issues.isEmpty()
}

class BlowDetector(
    private val frameSec: Double,
    private val floorDb: Double,
    private val minDurationSec: Double = 6.0,
    private val maxWaitSec: Double = 8.0,
    private val maxBlowSec: Double = 20.0,
) {
    private val startThr = floorDb + 15.0
    private val endThr = floorDb + 8.0
    private var t = 0.0
    private var startT = -1.0
    private var peakDb = -200.0
    private var peakT = 0.0
    private var quiet = 0
    private var interrupted = false
    private var clipped = false
    private var endT = -1.0
    var done = false; private set
    val elapsedBlowSec: Double get() = if (startT < 0) 0.0 else (if (endT >= 0) endT else t) - startT
    val blowing: Boolean get() = startT >= 0 && !done

    /** Feeds one frame; returns true when the trial is finished. */
    fun add(db: Double, clip: Boolean): Boolean {
        if (done) return true
        if (startT < 0) {
            if (db >= startThr) { startT = t; peakDb = db; peakT = t }
            else if (t >= maxWaitSec) done = true
        } else {
            if (clip) clipped = true
            if (db > peakDb) { peakDb = db; peakT = t }
            if (db < endThr) {
                quiet++
                if (quiet * frameSec >= 0.3) { endT = t - (quiet - 1) * frameSec; done = true }
            } else {
                // A short drop below the end level followed by recovery is a pause or a cough.
                if (quiet in 2..5) interrupted = true
                quiet = 0
            }
            if (!done && t - startT >= maxBlowSec) { endT = t; done = true }
        }
        t += frameSec
        return done
    }

    fun result(): BlowTrial {
        if (startT < 0) return BlowTrial(0.0, 0.0, 0.0, setOf(BlowIssue.NO_BLOW))
        val dur = elapsedBlowSec
        val ttp = peakT - startT
        val issues = mutableSetOf<BlowIssue>()
        if (ttp > 0.5) issues += BlowIssue.SLOW_START
        if (dur < minDurationSec) issues += BlowIssue.TOO_SHORT
        if (interrupted) issues += BlowIssue.INTERRUPTED
        if (clipped) issues += BlowIssue.CLIPPED
        return BlowTrial(dur, peakDb - floorDb, ttp, issues)
    }
}

object BreathSession {
    /** Analogue of the 150 mL rule: two best acceptable trials within 10 % in duration and 3 dB in peak. */
    fun repeatable(trials: List<BlowTrial>): Boolean {
        val ok = trials.filter { it.acceptable }.sortedByDescending { it.durationSec }
        if (ok.size < 2) return false
        val a = ok[0]; val b = ok[1]
        return (a.durationSec - b.durationSec) / a.durationSec <= 0.10 &&
            kotlin.math.abs(a.peakAboveFloorDb - b.peakAboveFloorDb) <= 3.0
    }

    /** ATS/ERS: at least three acceptable manoeuvres; stop after eight attempts. */
    fun complete(trials: List<BlowTrial>): Boolean =
        (trials.count { it.acceptable } >= 3 && repeatable(trials)) || trials.size >= 8

    fun best(trials: List<BlowTrial>): BlowTrial? =
        trials.filter { it.acceptable }.maxByOrNull { it.durationSec } ?: trials.maxByOrNull { it.durationSec }
}
