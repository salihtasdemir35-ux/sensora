package com.sensora.app.core.math

enum class UnitCategory { LENGTH, AREA, VOLUME, MASS, SPEED, TEMPERATURE, PRESSURE, ENERGY, POWER }

/** [toBase] multiplies a value in this unit to get the category's SI base unit (ignored for temperature). */
data class UnitDef(val id: String, val symbol: String, val toBase: Double)

object UnitConverter {
    private val table: Map<UnitCategory, List<UnitDef>> = mapOf(
        UnitCategory.LENGTH to listOf(
            UnitDef("mm", "mm", 0.001), UnitDef("cm", "cm", 0.01), UnitDef("m", "m", 1.0),
            UnitDef("km", "km", 1000.0), UnitDef("in", "in", 0.0254), UnitDef("ft", "ft", 0.3048),
            UnitDef("yd", "yd", 0.9144), UnitDef("mi", "mi", 1609.344), UnitDef("nmi", "nmi", 1852.0),
        ),
        UnitCategory.AREA to listOf(
            UnitDef("cm2", "cm²", 1e-4), UnitDef("m2", "m²", 1.0), UnitDef("ha", "ha", 1e4),
            UnitDef("km2", "km²", 1e6), UnitDef("in2", "in²", 0.00064516), UnitDef("ft2", "ft²", 0.09290304),
            UnitDef("ac", "ac", 4046.8564224),
        ),
        UnitCategory.VOLUME to listOf(
            UnitDef("ml", "mL", 1e-6), UnitDef("l", "L", 1e-3), UnitDef("m3", "m³", 1.0),
            UnitDef("cup", "cup (US)", 2.365882365e-4), UnitDef("floz", "fl oz (US)", 2.95735295625e-5),
            UnitDef("gal", "gal (US)", 0.003785411784), UnitDef("ft3", "ft³", 0.028316846592),
        ),
        UnitCategory.MASS to listOf(
            UnitDef("mg", "mg", 1e-6), UnitDef("g", "g", 1e-3), UnitDef("kg", "kg", 1.0),
            UnitDef("t", "t", 1000.0), UnitDef("oz", "oz", 0.028349523125), UnitDef("lb", "lb", 0.45359237),
            UnitDef("st", "st", 6.35029318),
        ),
        UnitCategory.SPEED to listOf(
            UnitDef("mps", "m/s", 1.0), UnitDef("kmh", "km/h", 1.0 / 3.6), UnitDef("mph", "mph", 0.44704),
            UnitDef("kn", "kn", 1852.0 / 3600.0), UnitDef("fps", "ft/s", 0.3048),
        ),
        UnitCategory.TEMPERATURE to listOf(
            UnitDef("C", "°C", 1.0), UnitDef("F", "°F", 1.0), UnitDef("K", "K", 1.0),
        ),
        UnitCategory.PRESSURE to listOf(
            UnitDef("pa", "Pa", 1.0), UnitDef("hpa", "hPa", 100.0), UnitDef("kpa", "kPa", 1000.0),
            UnitDef("bar", "bar", 1e5), UnitDef("atm", "atm", 101325.0), UnitDef("mmhg", "mmHg", 133.322387415),
            UnitDef("inhg", "inHg", 3386.389), UnitDef("psi", "psi", 6894.757293168),
        ),
        UnitCategory.ENERGY to listOf(
            UnitDef("j", "J", 1.0), UnitDef("kj", "kJ", 1000.0), UnitDef("cal", "cal", 4.184),
            UnitDef("kcal", "kcal", 4184.0), UnitDef("wh", "Wh", 3600.0), UnitDef("kwh", "kWh", 3.6e6),
            UnitDef("btu", "BTU", 1055.05585262),
        ),
        UnitCategory.POWER to listOf(
            UnitDef("w", "W", 1.0), UnitDef("kw", "kW", 1000.0), UnitDef("hp", "hp", 745.69987158227),
            UnitDef("ps", "PS", 735.49875), UnitDef("btuh", "BTU/h", 0.29307107),
        ),
    )

    fun units(category: UnitCategory): List<UnitDef> = table.getValue(category)

    fun convert(value: Double, category: UnitCategory, fromId: String, toId: String): Double {
        if (category == UnitCategory.TEMPERATURE) {
            val c = when (fromId) { "F" -> (value - 32.0) * 5.0 / 9.0; "K" -> value - 273.15; else -> value }
            return when (toId) { "F" -> c * 9.0 / 5.0 + 32.0; "K" -> c + 273.15; else -> c }
        }
        val list = units(category)
        val from = list.first { it.id == fromId }
        val to = list.first { it.id == toId }
        return value * from.toBase / to.toBase
    }
}
