package com.sensora.app.core

import com.sensora.app.core.astro.AstroTime
import com.sensora.app.core.astro.Body
import com.sensora.app.core.astro.CatalogParser
import com.sensora.app.core.astro.Coords
import com.sensora.app.core.astro.Equatorial
import com.sensora.app.core.astro.HorizonFrame
import com.sensora.app.core.astro.MeteorShowers
import com.sensora.app.core.astro.MoonInfo
import com.sensora.app.core.astro.ObservationMethod
import com.sensora.app.core.astro.Observer
import com.sensora.app.core.astro.SolarSystem
import com.sensora.app.core.astro.SunTimes
import com.sensora.app.core.astro.TonightPlanner
import com.sensora.app.core.astro.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.abs

private fun utc(s: String) = Instant.parse(s).toEpochMilli()
private fun raDiff(a: Double, b: Double): Double { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }

/** Reference values: Jean Meeus, Astronomical Algorithms, 2nd ed., worked examples. */
class EphemerisTest {
    @Test fun siderealTimeMeeus12a() = assertEquals(197.693195, AstroTime.gmstDeg(2446895.5), 1e-5)

    @Test fun sunMeeus25a() {
        val p = SolarSystem.position(Body.SUN, 2448908.5)
        assertTrue("RA ${p.equatorial.raDeg}", raDiff(p.equatorial.raDeg, 198.38083) < 0.05)
        assertEquals(-7.78507, p.equatorial.decDeg, 0.05)
    }

    @Test fun venusMeeus33a() {
        val p = SolarSystem.position(Body.VENUS, 2448976.5)
        assertTrue("RA ${p.equatorial.raDeg}", raDiff(p.equatorial.raDeg, 316.17291) < 0.1)
        assertEquals(-18.88801, p.equatorial.decDeg, 0.1)
        assertTrue("mag ${p.magnitude}", p.magnitude in -4.6..-3.8)
    }

    @Test fun moonMeeus47a() {
        val p = SolarSystem.position(Body.MOON, 2448724.5)
        assertTrue("RA ${p.equatorial.raDeg}", raDiff(p.equatorial.raDeg, 134.688470) < 0.3)
        assertEquals(13.768368, p.equatorial.decDeg, 0.3)
        assertEquals(57.76, p.distance, 0.3)
    }

    @Test fun precessionMeeus21b() {
        val r = Coords.precessFromJ2000(Equatorial(41.04994, 49.228467), 2462088.69)
        assertEquals(41.54309, r.raDeg, 0.002)
        assertEquals(49.34920, r.decDeg, 0.002)
    }

    @Test fun horizonFrameMatchesFormula() {
        val eq = Equatorial(123.4, 21.7)
        val h1 = Coords.toHorizontal(eq, 39.9, 200.0)
        val out = DoubleArray(3)
        val v = Coords.unitVector(eq)
        HorizonFrame(39.9, 200.0).toHorizon(v[0], v[1], v[2], out)
        val h2 = HorizonFrame.azAlt(out)
        assertEquals(h1.altDeg, h2.altDeg, 1e-9); assertEquals(h1.azDeg, h2.azDeg, 1e-9)
    }

    @Test fun westernHorizon() {
        val h = Coords.toHorizontal(Equatorial(0.0, 0.0), 0.0, 90.0)
        assertEquals(0.0, h.altDeg, 1e-9); assertEquals(270.0, h.azDeg, 1e-9)
    }
}

class SunMoonTest {
    private val ankara = Observer(39.93, 32.86)

    @Test fun equatorDayLength() {
        val d = SunTimes.compute(Observer(0.0, 0.0), utc("2026-03-20T00:00:00Z"))
        val min = d.dayLengthMs!! / 60_000
        assertTrue("$min", min in 724..730)
    }

    @Test fun ankaraSolstice() {
        val d = SunTimes.compute(ankara, utc("2026-06-20T21:00:00Z"))
        val min = d.dayLengthMs!! / 60_000
        assertTrue("$min", min in 890..908)
        // Sunrise around 05:20 local (02:20 UTC)
        val rise = d.events.rise!!
        assertTrue(abs(rise - utc("2026-06-21T02:20:00Z")) < 15 * 60_000)
    }

    @Test fun polarDayAndNight() {
        val tromso = Observer(69.65, 18.96)
        assertTrue(SunTimes.compute(tromso, utc("2026-06-21T00:00:00Z")).events.alwaysUp)
        assertTrue(SunTimes.compute(tromso, utc("2026-12-21T00:00:00Z")).events.alwaysDown)
    }

    @Test fun knownFullAndNewMoon() {
        val full = MoonInfo.next(utc("2024-04-20T00:00:00Z"), 180.0)
        assertTrue("${Instant.ofEpochMilli(full)}", abs(full - utc("2024-04-23T23:49:00Z")) < 3 * 3_600_000)
        val newMoon = MoonInfo.next(utc("2024-04-01T00:00:00Z"), 0.0)
        assertTrue("${Instant.ofEpochMilli(newMoon)}", abs(newMoon - utc("2024-04-08T18:21:00Z")) < 3 * 3_600_000)
        val p = MoonInfo.phase(utc("2024-04-23T23:49:00Z"))
        assertTrue(p.illuminated > 0.98); assertEquals(4, p.phaseIndex)
    }

    @Test fun nightWindowAnkaraOctober() {
        val w = TonightPlanner.nightWindow(ankara, utc("2026-10-17T15:00:00Z"))!!
        assertEquals(-18.0, w.darknessDeg, 0.0)
        assertTrue("${Instant.ofEpochMilli(w.startMs)}", w.startMs in utc("2026-10-17T16:00:00Z")..utc("2026-10-17T17:30:00Z"))
        assertTrue("${Instant.ofEpochMilli(w.endMs)}", w.endMs in utc("2026-10-18T02:30:00Z")..utc("2026-10-18T03:45:00Z"))
    }

    @Test fun meteorShowers() {
        val orionids = MeteorShowers.all.first { it.id == "orionids" }
        val quad = MeteorShowers.all.first { it.id == "quadrantids" }
        assertTrue(MeteorShowers.isActive(orionids, 10, 17))
        assertTrue(MeteorShowers.isActive(quad, 1, 1)); assertTrue(MeteorShowers.isActive(quad, 12, 30))
        assertTrue(!MeteorShowers.isActive(quad, 2, 1))
    }

    @Test fun observationMethod() {
        assertEquals(ObservationMethod.NAKED_EYE, Visibility.method(3.4, extended = true))
        assertEquals(ObservationMethod.BINOCULARS, Visibility.method(5.9, extended = true))
        assertEquals(ObservationMethod.TELESCOPE, Visibility.method(9.0, extended = true))
    }
}

class CatalogTest {
    private fun asset(name: String): String {
        val candidates = listOf("src/main/assets/sky/$name", "app/src/main/assets/sky/$name",
            System.getProperty("sensora.assets", "") + "/$name")
        return File(candidates.first { File(it).exists() }).readText()
    }

    @Test fun catalogsParse() {
        val stars = CatalogParser.stars(asset("stars.tsv"))
        assertTrue(stars.size > 5000)
        assertEquals("Sirius", stars.minByOrNull { it.mag }!!.name)
        val cons = CatalogParser.constellations(asset("constellations.tsv"))
        assertEquals(88, cons.size)
        assertEquals("Büyük Ayı", cons.first { it.abbr == "UMa" }.name("tr"))
        val dso = CatalogParser.deepSky(asset("dso.tsv"))
        assertTrue(dso.any { it.id == "M45" && it.name == "Pleiades" })
        assertTrue(dso.size >= 110)
    }
}
