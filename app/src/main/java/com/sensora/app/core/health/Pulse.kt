package com.sensora.app.core.health

import com.sensora.app.core.model.Quality
import kotlin.math.abs
import kotlin.math.sqrt

data class PulseResult(val bpm: Double?, val quality: Quality, val rmssdMs: Double?, val beats: Int, val correlation: Double)

/**
 * Heart rate from fingertip photoplethysmography (camera + flash). The brightness of the finger
 * changes slightly with every heartbeat. Signal quality decides whether a result is shown at all.
 */
object PulseAnalyzer {
    private fun movingAverage(v: DoubleArray, w: Int): DoubleArray {
        val out = DoubleArray(v.size)
        var sum = 0.0
        val half = w / 2
        val pre = DoubleArray(v.size + 1)
        for (i in v.indices) { sum += v[i]; pre[i + 1] = sum }
        for (i in v.indices) {
            val a = maxOf(0, i - half); val b = minOf(v.size, i + half + 1)
            out[i] = (pre[b] - pre[a]) / (b - a)
        }
        return out
    }

    fun resample(times: DoubleArray, values: DoubleArray, fs: Double): DoubleArray {
        if (times.size < 2) return DoubleArray(0)
        val n = ((times.last() - times.first()) * fs).toInt()
        val out = DoubleArray(n)
        var j = 0
        for (k in 0 until n) {
            val tk = times.first() + k / fs
            while (j < times.size - 2 && times[j + 1] < tk) j++
            val span = times[j + 1] - times[j]
            val f = if (span > 0) ((tk - times[j]) / span).coerceIn(0.0, 1.0) else 0.0
            out[k] = values[j] + f * (values[j + 1] - values[j])
        }
        return out
    }

    fun analyze(times: DoubleArray, values: DoubleArray, fs: Double = 30.0): PulseResult {
        if (times.size < 2 || times.last() - times.first() < 10.0) return PulseResult(null, Quality.UNRELIABLE, null, 0, 0.0)
        val raw = resample(times, values, fs)
        val trend = movingAverage(raw, fs.toInt())
        var sig = DoubleArray(raw.size) { raw[it] - trend[it] }
        sig = movingAverage(sig, 5)
        // Invert: more blood in the finger means a darker image, so peaks become systolic beats.
        for (i in sig.indices) sig[i] = -sig[i]
        val mean = sig.average()
        val sd = sqrt(sig.sumOf { (it - mean) * (it - mean) } / sig.size)
        if (sd < 1e-9) return PulseResult(null, Quality.UNRELIABLE, null, 0, 0.0)
        val z = DoubleArray(sig.size) { (sig[it] - mean) / sd }

        val minLag = (fs * 60.0 / 200.0).toInt(); val maxLag = (fs * 60.0 / 40.0).toInt()
        val r = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var s = 0.0
            for (i in 0 until z.size - lag) s += z[i] * z[i + lag]
            r[lag] = s / (z.size - lag)
        }
        val maxR = (minLag..maxLag).maxOf { r[it] }
        // The fundamental period is the shortest lag with a strong local maximum; longer lags are multiples.
        var bestLag = (minLag..maxLag).first { r[it] == maxR }
        for (lag in minLag..maxLag) {
            if (r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1] && r[lag] >= 0.8 * maxR) { bestLag = lag; break }
        }
        val bestR = r[bestLag]
        val bpmAc = 60.0 * fs / bestLag

        val peaks = ArrayList<Int>()
        val minDist = (bestLag * 0.6).toInt().coerceAtLeast(1)
        for (i in 1 until z.size - 1) {
            if (z[i] > 0.3 && z[i] >= z[i - 1] && z[i] > z[i + 1]) {
                if (peaks.isEmpty() || i - peaks.last() >= minDist) peaks.add(i)
                else if (z[i] > z[peaks.last()]) peaks[peaks.size - 1] = i
            }
        }
        val ibis = peaks.zipWithNext { a, b -> (b - a) / fs }
        val bpmPk = if (ibis.size >= 4) 60.0 / ibis.sorted()[ibis.size / 2] else null
        val consistent = bpmPk != null && abs(bpmPk - bpmAc) / bpmAc < 0.10
        val quality = when {
            bestR >= 0.6 && consistent -> Quality.GOOD
            bestR >= 0.4 && consistent -> Quality.LIMITED
            else -> Quality.UNRELIABLE
        }
        if (quality == Quality.UNRELIABLE) return PulseResult(null, quality, null, peaks.size, bestR)
        val rmssd = if (quality == Quality.GOOD && ibis.size >= 10) {
            val d = ibis.zipWithNext { a, b -> (b - a) * 1000.0 }
            sqrt(d.sumOf { it * it } / d.size)
        } else null
        return PulseResult(bpmPk, quality, rmssd, peaks.size, bestR)
    }

    fun urgency(bpm: Double): Urgency = when { bpm < 40 || bpm > 130 -> Urgency.SOON; bpm < 50 || bpm > 100 -> Urgency.DISCUSS; else -> Urgency.NORMAL }
}
