package com.sensora.app.core

import com.sensora.app.core.steps.AccelStepDetector
import com.sensora.app.core.steps.StepDayState
import com.sensora.app.core.steps.StepDayTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StepDayTrackerTest {
    @Test fun firstUseStartsAtZero() {
        val u = StepDayTracker.update(StepDayState(), 1000, "2026-10-16")
        assertEquals(0, u.todaySteps); assertNull(u.closedDay)
    }
    @Test fun countsRebootAndNewDay() {
        var u = StepDayTracker.update(StepDayState(), 1000, "2026-10-16")
        u = StepDayTracker.update(u.state, 1500, "2026-10-16")
        assertEquals(500, u.todaySteps)
        u = StepDayTracker.update(u.state, 20, "2026-10-16")      // reboot
        assertEquals(520, u.todaySteps)
        u = StepDayTracker.update(u.state, 100, "2026-10-17")     // next day
        assertEquals("2026-10-16" to 520L, u.closedDay)
        assertEquals(80, u.todaySteps)
    }
    @Test fun newDayAfterReboot() {
        var u = StepDayTracker.update(StepDayState(), 5000, "2026-10-16")
        u = StepDayTracker.update(u.state, 5200, "2026-10-16")
        u = StepDayTracker.update(u.state, 30, "2026-10-17")
        assertEquals(0, u.todaySteps)
        u = StepDayTracker.update(u.state, 90, "2026-10-17")
        assertEquals(60, u.todaySteps)
    }
    @Test fun stepLength() = assertEquals(0.7055, StepDayTracker.stepLengthM(170), 1e-4)
}

class AccelStepDetectorTest {
    @Test fun twoHertzWalkFor10Seconds() {
        val d = AccelStepDetector()
        for (i in 0 until 500) {
            val t = i / 50.0
            val m = (9.81 + 3 * sin(2 * PI * 2 * t)).toFloat()
            d.onSample(0f, 0f, m, (t * 1000).toLong())
        }
        assertEquals(20, d.steps)
    }
    @Test fun stillPhoneCountsNothing() {
        val d = AccelStepDetector()
        for (i in 0 until 500) d.onSample(0.1f, 0.2f, 9.8f, i * 20L)
        assertEquals(0, d.steps)
    }
}
