package com.sensora.app.core.quake

import com.sensora.app.core.math.GeoMath
import com.sensora.app.core.util.MiniJson
import com.sensora.app.core.util.asDouble
import com.sensora.app.core.util.asList
import com.sensora.app.core.util.asMap
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class QuakeSource { AFAD, USGS }

data class Earthquake(
    val id: String,
    val source: QuakeSource,
    val timeMs: Long,
    val magnitude: Double,
    val depthKm: Double,
    val lat: Double,
    val lon: Double,
    val place: String,
) {
    fun distanceKm(lat0: Double, lon0: Double) = GeoMath.haversine(lat0, lon0, lat, lon) / 1000.0
}

object QuakeParsers {
    /** AFAD publishes local Turkish time without an offset (UTC+3, no daylight saving). */
    val AFAD_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")

    fun afad(json: String): List<Earthquake> = (MiniJson.parse(json).asList() ?: emptyList()).mapNotNull { e ->
        val m = e.asMap() ?: return@mapNotNull null
        val mag = m["magnitude"].asDouble() ?: return@mapNotNull null
        val lat = m["latitude"].asDouble() ?: return@mapNotNull null
        val lon = m["longitude"].asDouble() ?: return@mapNotNull null
        val date = (m["date"] as? String) ?: return@mapNotNull null
        val t = runCatching { LocalDateTime.parse(date.take(19)).atZone(AFAD_ZONE).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
        val place = listOfNotNull(m["location"] as? String, listOfNotNull(m["district"] as? String, m["province"] as? String).joinToString(", ").ifBlank { null })
            .firstOrNull { it.isNotBlank() } ?: ""
        Earthquake(((m["eventID"] ?: m["eventId"])?.toString() ?: date), QuakeSource.AFAD, t, mag, m["depth"].asDouble() ?: 0.0, lat, lon, place)
    }

    fun usgs(json: String): List<Earthquake> {
        val features = MiniJson.parse(json).asMap()?.get("features").asList() ?: return emptyList()
        return features.mapNotNull { f ->
            val fm = f.asMap() ?: return@mapNotNull null
            val p = fm["properties"].asMap() ?: return@mapNotNull null
            val c = fm["geometry"].asMap()?.get("coordinates").asList() ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            Earthquake(
                id = fm["id"]?.toString() ?: return@mapNotNull null,
                source = QuakeSource.USGS,
                timeMs = p["time"].asDouble()?.toLong() ?: return@mapNotNull null,
                magnitude = p["mag"].asDouble() ?: return@mapNotNull null,
                depthKm = c.getOrNull(2).asDouble() ?: 0.0,
                lat = c[1].asDouble() ?: return@mapNotNull null,
                lon = c[0].asDouble() ?: return@mapNotNull null,
                place = p["place"] as? String ?: "",
            )
        }
    }

    fun afadUrl(nowMs: Long, hours: Int, minMag: Double): String {
        // AFAD expects yyyy-MM-ddTHH:mm:ss; LocalDateTime.toString() would drop ":00" seconds.
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val end = Instant.ofEpochMilli(nowMs).atZone(AFAD_ZONE).toLocalDateTime()
        val start = end.minusHours(hours.toLong())
        return "https://deprem.afad.gov.tr/apiv2/event/filter?start=${start.format(f)}&end=${end.format(f)}&minmag=$minMag&orderby=timedesc&limit=500&format=json"
    }

    fun usgsUrl(hours: Int, minMag: Double): String {
        val feed = if (hours <= 24) "day" else "week"
        val level = when { minMag >= 4.5 -> "4.5"; minMag >= 2.5 -> "2.5"; else -> "1.0" }
        return "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/${level}_$feed.geojson"
    }
}

/** Shaking intensity from peak ground acceleration (Wald et al. 1999, California). Indicative only. */
object Intensity {
    fun mmiFromPga(pgaMs2: Double): Double {
        val cm = pgaMs2 * 100.0
        if (cm <= 0.0) return 1.0
        val high = 3.66 * log10(cm) - 1.66
        val mmi = if (high >= 5.0) high else 2.20 * log10(cm) + 1.00
        return mmi.coerceIn(1.0, 10.0)
    }

    fun roman(mmi: Double): String = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")[(mmi.toInt() - 1).coerceIn(0, 9)]
}

/** In-place radix-2 FFT and spectrum helpers for vibration analysis. */
object Fft {
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "size must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** One-sided amplitude spectrum of [signal] (mean removed, Hann window). */
    fun amplitudeSpectrum(signal: DoubleArray): DoubleArray {
        val n = signal.size
        val mean = signal.average()
        val re = DoubleArray(n) { (signal[it] - mean) * (0.5 - 0.5 * cos(2 * PI * it / (n - 1))) }
        val im = DoubleArray(n)
        fft(re, im)
        return DoubleArray(n / 2) { k -> 4.0 * sqrt(re[k] * re[k] + im[k] * im[k]) / n }   // ×2 one-sided, ×2 Hann gain
    }

    data class Peak(val frequencyHz: Double, val amplitude: Double)

    fun dominant(spectrum: DoubleArray, sampleRate: Double, n: Int): Peak {
        var best = 1
        for (k in 2 until spectrum.size) if (spectrum[k] > spectrum[best]) best = k
        return Peak(best * sampleRate / n, spectrum[best])
    }
}

/** Ring buffer of acceleration samples with peak and RMS statistics. */
class VibrationBuffer(private val capacity: Int = 4096) {
    private val t = LongArray(capacity)
    private val x = FloatArray(capacity); private val y = FloatArray(capacity); private val z = FloatArray(capacity)
    private var head = 0
    var count = 0; private set
    var peak = 0.0; private set

    fun add(timeNs: Long, ax: Float, ay: Float, az: Float) {
        t[head] = timeNs; x[head] = ax; y[head] = ay; z[head] = az
        head = (head + 1) % capacity
        if (count < capacity) count++
        val m = sqrt((ax * ax + ay * ay + az * az).toDouble())
        if (m > peak) peak = m
    }

    fun resetPeak() { peak = 0.0 }

    private fun idx(k: Int) = (head - count + k + capacity) % capacity

    /** Last [n] samples of one axis (0, 1, 2) or the vector magnitude (3), oldest first. */
    fun last(n: Int, axis: Int): DoubleArray {
        val m = minOf(n, count)
        return DoubleArray(m) { k ->
            val i = idx(count - m + k)
            when (axis) { 0 -> x[i].toDouble(); 1 -> y[i].toDouble(); 2 -> z[i].toDouble(); else -> sqrt((x[i] * x[i] + y[i] * y[i] + z[i] * z[i]).toDouble()) }
        }
    }

    /** Average sampling rate over the buffered samples. */
    fun sampleRate(): Double {
        if (count < 2) return 0.0
        val dt = (t[idx(count - 1)] - t[idx(0)]) / 1e9
        return if (dt > 0) (count - 1) / dt else 0.0
    }

    fun rms(n: Int): Double {
        val v = last(n, 3)
        return if (v.isEmpty()) 0.0 else sqrt(v.sumOf { it * it } / v.size)
    }
}
