package com.sensora.app.core

import com.sensora.app.data.Exporters
import com.sensora.app.data.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {
    private val r = MeasurementRecord(id = 1, toolId = "level", category = "DIRECTION", value = 1.5, unit = "°",
        details = "X, Y", method = "gravity", quality = "GOOD", timestamp = 0, note = "=SUM(A1) \"hi\"")

    @Test fun csvEscapesAndBlocksFormulas() {
        val csv = Exporters.toCsv(listOf(r))
        val line = csv.lines()[1]
        assertTrue(line.contains("\"X, Y\""))
        assertTrue(line.contains("\"'=SUM(A1) \"\"hi\"\"\""))
        assertTrue(line.startsWith("1,1970-01-01T00:00:00Z,level,"))
    }
    @Test fun jsonEscapes() {
        assertEquals("\"a\\\"b\\n\"", Exporters.jsonString("a\"b\n"))
        assertEquals("null", Exporters.jsonNumber(Double.NaN))
        val json = Exporters.toJson(listOf(r), 0)
        assertTrue(json.startsWith("{\"app\":\"Sensora\",\"format\":1"))
        assertTrue(json.contains("\"value\":1.5"))
        assertTrue(json.contains("\"latitude\":null"))
    }
}
