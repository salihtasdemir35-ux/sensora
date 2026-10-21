package com.sensora.app.core.vision

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** Optotype geometry: an optotype subtends 5 arc minutes at logMAR 0. */
object Optotype {
    fun letterHeightMm(logMar: Double, distanceMm: Double): Double =
        distanceMm * tan(Math.toRadians(5.0 / 60.0 * 10.0.pow(logMar)))
}

enum class Direction { UP, RIGHT, DOWN, LEFT }

/**
 * ETDRS-style letter-by-letter scoring with 5 optotypes per line in 0.1 logMAR steps.
 * Each correct optotype is worth 0.02 logMAR; the test stops when a line has 2 or fewer correct.
 * Lines are kept as tenths (7 = logMAR 0.7) to avoid rounding errors.
 */
class EtdrsScorer(start: Int = 7, private val minLine: Int = -3, private val maxLine: Int = 10) {
    var topLine = start; private set
    var line = start; private set
    private var onLine = 0
    private var correctOnLine = 0
    private var total = 0
    var done = false; private set

    fun record(correct: Boolean) {
        if (done) return
        onLine++
        if (correct) { correctOnLine++; total++ }
        if (onLine < 5) return
        if (correctOnLine <= 2) {
            if (line == topLine && topLine < maxLine) {
                // Start line too small: restart three lines larger.
                topLine = minOf(maxLine, topLine + 3); line = topLine; total = 0
            } else done = true
        } else if (line - 1 < minLine) {
            done = true
        } else line -= 1
        onLine = 0; correctOnLine = 0
    }

    /** Called when the next line would be smaller than the screen can draw. */
    fun stop() { done = true }

    val score: Double get() = (topLine + 1) / 10.0 - 0.02 * total
}

/** Pelli–Robson-style contrast test: triplets in 0.15 log-unit steps; score = last triplet with ≥2 correct. */
class ContrastScorer(private val levels: Int = 16) {
    var level = 0; private set
    private var shown = 0
    private var correct = 0
    var lastPassed = -1; private set
    var done = false; private set

    val contrast: Double get() = 10.0.pow(-0.15 * level)

    fun record(ok: Boolean) {
        if (done) return
        shown++; if (ok) correct++
        if (shown < 3) return
        if (correct >= 2) { lastPassed = level; level++; if (level >= levels) done = true } else done = true
        shown = 0; correct = 0
    }

    fun stop() { done = true }

    val logCs: Double get() = if (lastPassed < 0) 0.0 else 0.15 * lastPassed

    companion object {
        fun interpretation(logCs: Double): Int = when { logCs >= 1.65 -> 0; logCs >= 1.5 -> 1; else -> 2 }
    }
}

/** sRGB colour science (D65) used to draw contrast targets and colour-vision plates. */
object ColorScience {
    fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    fun toGamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    fun rgbToXyz(r: Double, g: Double, b: Double): DoubleArray {
        val R = toLinear(r); val G = toLinear(g); val B = toLinear(b)
        return doubleArrayOf(
            0.4124 * R + 0.3576 * G + 0.1805 * B,
            0.2126 * R + 0.7152 * G + 0.0722 * B,
            0.0193 * R + 0.1192 * G + 0.9505 * B,
        )
    }

    /** Returns sRGB in 0..1, or null when out of gamut. */
    fun xyzToRgb(x: Double, y: Double, z: Double): DoubleArray? {
        val r = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val g = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val b = 0.0557 * x - 0.2040 * y + 1.0570 * z
        if (listOf(r, g, b).any { it < -1e-9 || it > 1 + 1e-9 }) return null
        return doubleArrayOf(toGamma(r.coerceIn(0.0, 1.0)), toGamma(g.coerceIn(0.0, 1.0)), toGamma(b.coerceIn(0.0, 1.0)))
    }

    fun xyYToRgb(cx: Double, cy: Double, Y: Double): DoubleArray? =
        xyzToRgb(cx * Y / cy, Y, (1 - cx - cy) * Y / cy)

    /** 8-bit grey level whose luminance is (1 − contrast) of white (Weber contrast of a dark target). */
    fun greyForContrast(contrast: Double): Int = (255 * toGamma(1.0 - contrast)).roundToInt().coerceIn(0, 255)
    fun achievedContrast(grey: Int): Double = 1.0 - toLinear(grey / 255.0)
}

enum class ColorAxis { CONTROL, PROTAN, DEUTAN, TRITAN }

data class Dot(val x: Float, val y: Float, val r: Float, val rgb: Int)
data class Plate(val digit: Int, val axis: ColorAxis, val dots: List<Dot>)

/**
 * Pseudo-isochromatic plates generated from colour science (not a copy of any published test):
 * figure and background lie on a dichromatic confusion line through the copunctal point
 * (Smith & Pokorny), at equal mean luminance with random luminance noise per dot.
 */
object PlateGenerator {
    val COPUNCTAL = mapOf(ColorAxis.PROTAN to (0.7465 to 0.2535), ColorAxis.DEUTAN to (1.4 to -0.4), ColorAxis.TRITAN to (0.1748 to 0.0044))

    private val DIGITS = arrayOf(
        "01110100011001110101110011000101110", "00100011000010000100001000010001110", "01110100010000100010001000100011111",
        "11111000100010000010000011000101110", "00010001100101010010111110001000010", "11111100001111000001000011000101110",
        "00110010001000011110100011000101110", "11111000010001000100010000100001000", "01110100011000101110100011000101110",
        "01110100011000101111000010001001100",
    )

    fun digitCell(d: Int, col: Int, row: Int): Boolean = DIGITS[d][row * 5 + col] == '1'

    /** Figure and background chromaticities on the confusion line of [axis] around (cx, cy). */
    fun confusionPair(axis: ColorAxis, cx: Double, cy: Double, d: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val (px, py) = COPUNCTAL.getValue(axis)
        var ux = cx - px; var uy = cy - py
        val n = sqrt(ux * ux + uy * uy); ux /= n; uy /= n
        return (cx + d * ux to cy + d * uy) to (cx - d * ux to cy - d * uy)
    }

    private fun rgbInt(c: DoubleArray) = (0xFF shl 24) or ((c[0] * 255).roundToInt() shl 16) or ((c[1] * 255).roundToInt() shl 8) or (c[2] * 255).roundToInt()

    fun plate(axis: ColorAxis, digit: Int, seed: Int): Plate {
        val rnd = Random(seed)
        val center = when (axis) { ColorAxis.TRITAN -> 0.31 to 0.33; else -> 0.36 to 0.40 }
        val (fig, bg) = if (axis == ColorAxis.CONTROL) (0.30 to 0.55) to (0.50 to 0.38)   // green on orange: visible to almost everyone
        else {
            var d = 0.045
            var pair = confusionPair(axis, center.first, center.second, d)
            while ((ColorScience.xyYToRgb(pair.first.first, pair.first.second, 0.45) == null ||
                    ColorScience.xyYToRgb(pair.second.first, pair.second.second, 0.45) == null) && d > 0.01) {
                d -= 0.005; pair = confusionPair(axis, center.first, center.second, d)
            }
            pair
        }
        val dots = ArrayList<Dot>()
        var tries = 0
        while (dots.size < 420 && tries < 6000) {
            tries++
            val a = rnd.nextDouble(0.0, 2 * PI); val rr = sqrt(rnd.nextDouble()) * 0.95
            val x = (0.5 + 0.5 * rr * cos(a)).toFloat(); val y = (0.5 + 0.5 * rr * sin(a)).toFloat()
            val r = rnd.nextDouble(0.010, 0.022).toFloat()
            if (dots.any { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < (it.r + r) * (it.r + r) * 0.8f }) continue
            // Digit occupies the central 50 % × 70 % box on a 5 × 7 grid.
            val col = ((x - 0.25f) / 0.5f * 5).toInt(); val row = ((y - 0.15f) / 0.7f * 7).toInt()
            val inFigure = col in 0..4 && row in 0..6 && digitCell(digit, col, row)
            val (cx, cy) = if (inFigure) fig else bg
            val lum = rnd.nextDouble(0.30, 0.55)
            val rgb = ColorScience.xyYToRgb(cx, cy, lum) ?: ColorScience.xyYToRgb(cx, cy, 0.3) ?: continue
            dots.add(Dot(x, y, r, rgbInt(rgb)))
        }
        return Plate(digit, axis, dots)
    }

    /** 2 control, 3 protan, 3 deutan, 2 tritan plates with random digits (2–9). */
    fun series(seed: Int): List<Plate> {
        val rnd = Random(seed)
        val axes = listOf(ColorAxis.CONTROL, ColorAxis.PROTAN, ColorAxis.DEUTAN, ColorAxis.TRITAN, ColorAxis.PROTAN,
            ColorAxis.DEUTAN, ColorAxis.CONTROL, ColorAxis.TRITAN, ColorAxis.DEUTAN, ColorAxis.PROTAN)
        return axes.mapIndexed { i, ax -> plate(ax, rnd.nextInt(2, 10), seed * 31 + i) }
    }
}

data class ColorResult(val controlErrors: Int, val redGreenErrors: Int, val tritanErrors: Int) {
    val valid: Boolean get() = controlErrors == 0
    val redGreenSuspected: Boolean get() = valid && redGreenErrors >= 2
    val tritanSuspected: Boolean get() = valid && tritanErrors >= 1
}

object ColorScoring {
    fun score(plates: List<Plate>, answers: List<Int?>): ColorResult {
        var c = 0; var rg = 0; var t = 0
        plates.forEachIndexed { i, p ->
            if (answers.getOrNull(i) != p.digit) when (p.axis) {
                ColorAxis.CONTROL -> c++; ColorAxis.PROTAN, ColorAxis.DEUTAN -> rg++; ColorAxis.TRITAN -> t++
            }
        }
        return ColorResult(c, rg, t)
    }
}
