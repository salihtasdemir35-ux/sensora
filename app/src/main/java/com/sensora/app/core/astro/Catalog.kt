package com.sensora.app.core.astro

data class Star(val hip: Int, val eq: Equatorial, val mag: Double, val bv: Double?, val name: String, val designation: String)

data class Constellation(
    val abbr: String,
    val names: Map<String, String>,
    val label: Equatorial,
    val lines: List<List<Equatorial>>,
) {
    fun name(lang: String): String = names[lang] ?: names["la"] ?: abbr
}

data class DeepSky(
    val id: String,
    val name: String,
    val designation: String,
    val type: String,
    val mag: Double,
    val size: String,
    val eq: Equatorial,
    val distanceLy: Double?,
) {
    val displayName: String get() = if (name.isNotBlank()) "$id · $name" else id
}

class SkyCatalog(val stars: List<Star>, val constellations: List<Constellation>, val deepSky: List<DeepSky>)

/** Parses the TSV assets in app/src/main/assets/sky (see LICENSE-DATA.txt). */
object CatalogParser {
    fun stars(text: String): List<Star> = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val c = line.split('\t')
        if (c.size < 7) return@mapNotNull null
        Star(c[0].toIntOrNull() ?: 0, Equatorial(c[1].toDouble(), c[2].toDouble()), c[3].toDouble(), c[4].toDoubleOrNull(), c[5], c[6])
    }.toList()

    fun constellations(text: String): List<Constellation> = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val c = line.split('\t')
        if (c.size < 10) return@mapNotNull null
        val lines = c[9].split('|').filter { it.isNotBlank() }.map { poly ->
            poly.trim().split(' ').map { p -> val (ra, dec) = p.split(','); Equatorial(ra.toDouble(), dec.toDouble()) }
        }
        Constellation(
            abbr = c[0],
            names = mapOf("en" to c[3], "tr" to c[4], "de" to c[5], "es" to c[6], "ar" to c[7], "la" to c[8]),
            label = Equatorial(c[1].toDouble(), c[2].toDouble()),
            lines = lines,
        )
    }.toList()

    fun deepSky(text: String): List<DeepSky> = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val c = line.split('\t')
        if (c.size < 9) return@mapNotNull null
        DeepSky(c[0], c[1], c[2], c[3], c[4].toDoubleOrNull() ?: 99.0, c[5], Equatorial(c[6].toDouble(), c[7].toDouble()), c[8].toDoubleOrNull())
    }.toList()
}
