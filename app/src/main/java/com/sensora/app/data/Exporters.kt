package com.sensora.app.data

import java.time.Instant

/** CSV and JSON export without extra libraries (keeps it unit-testable on the JVM). */
object Exporters {
    private val COLUMNS = listOf(
        "id", "time_utc", "tool_id", "tool", "category", "value", "unit", "details",
        "method", "quality", "note", "latitude", "longitude", "favorite",
    )

    fun toCsv(records: List<MeasurementRecord>, toolName: (String) -> String = { it }): String {
        val sb = StringBuilder()
        sb.append(COLUMNS.joinToString(",")).append('\n')
        for (r in records) {
            val cells = listOf(
                r.id.toString(), Instant.ofEpochMilli(r.timestamp).toString(), r.toolId,
                safeText(toolName(r.toolId)), r.category, r.value.toString(), r.unit,
                safeText(r.details.orEmpty()), r.method, r.quality, safeText(r.note.orEmpty()),
                r.latitude?.toString().orEmpty(), r.longitude?.toString().orEmpty(), r.favorite.toString(),
            )
            sb.append(cells.joinToString(",") { csvEscape(it) }).append('\n')
        }
        return sb.toString()
    }

    fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** Prevents spreadsheet formula injection from free-text fields. */
    fun safeText(s: String): String = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s

    fun toJson(records: List<MeasurementRecord>, exportedAtMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append("{\"app\":\"Sensora\",\"format\":1,\"exported\":")
            .append(jsonString(Instant.ofEpochMilli(exportedAtMs).toString()))
            .append(",\"measurements\":[")
        records.forEachIndexed { i, r ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":").append(r.id)
                .append(",\"timestamp\":").append(r.timestamp)
                .append(",\"toolId\":").append(jsonString(r.toolId))
                .append(",\"category\":").append(jsonString(r.category))
                .append(",\"value\":").append(jsonNumber(r.value))
                .append(",\"unit\":").append(jsonString(r.unit))
                .append(",\"details\":").append(jsonString(r.details))
                .append(",\"method\":").append(jsonString(r.method))
                .append(",\"quality\":").append(jsonString(r.quality))
                .append(",\"latitude\":").append(jsonNumber(r.latitude))
                .append(",\"longitude\":").append(jsonNumber(r.longitude))
                .append(",\"note\":").append(jsonString(r.note))
                .append(",\"favorite\":").append(r.favorite)
                .append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun jsonString(s: String?): String {
        if (s == null) return "null"
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun jsonNumber(d: Double?): String = if (d == null || d.isNaN() || d.isInfinite()) "null" else d.toString()
}
