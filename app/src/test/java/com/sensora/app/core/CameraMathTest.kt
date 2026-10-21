package com.sensora.app.core

import com.sensora.app.core.camera.AngleMeasure
import com.sensora.app.core.camera.ArQuality
import com.sensora.app.core.camera.Geometry
import com.sensora.app.core.camera.HitKind
import com.sensora.app.core.camera.MeasureMath
import com.sensora.app.core.camera.ReferenceScale
import com.sensora.app.core.camera.Vec3
import com.sensora.app.core.model.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MeasureMathTest {
    @Test fun distanceAndPath() {
        val pts = listOf(Vec3(0.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0), Vec3(3.0, 4.0, 0.0))
        assertEquals(5.0, MeasureMath.distance(pts[0], pts[2]), 1e-9)
        assertEquals(7.0, MeasureMath.pathLength(pts), 1e-9)
        assertEquals(12.0, MeasureMath.perimeter(pts), 1e-9)
    }

    @Test fun areaOfTiltedRectangle() {
        // 2 m × 3 m rectangle on a plane tilted 30° — area must not depend on orientation.
        val c = cos(PI / 6); val s = sin(PI / 6)
        val pts = listOf(Vec3(0.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0), Vec3(2.0, 3 * c, 3 * s), Vec3(0.0, 3 * c, 3 * s))
        assertEquals(6.0, MeasureMath.polygonArea(pts), 1e-9)
        assertEquals(0.0, MeasureMath.polygonArea(pts.take(2)), 0.0)
    }

    @Test fun arQualityRules() {
        assertEquals(Quality.GOOD, ArQuality.of(true, HitKind.PLANE, 1.5))
        assertEquals(Quality.LIMITED, ArQuality.of(true, HitKind.DEPTH, 1.5))
        assertEquals(Quality.UNRELIABLE, ArQuality.of(true, HitKind.POINT, 3.0))
        assertEquals(Quality.UNRELIABLE, ArQuality.of(false, HitKind.PLANE, 1.0))
        assertEquals(Quality.UNRELIABLE, ArQuality.of(true, HitKind.PLANE, 7.0))
    }
}

class AngleMeasureTest {
    @Test fun elevationFromGravity() {
        assertEquals(0.0, AngleMeasure.elevationFromGravity(0.0, 9.81, 0.0), 1e-9)   // upright, camera at horizon
        assertEquals(90.0, AngleMeasure.elevationFromGravity(0.0, 0.0, -9.81), 1e-9) // camera pointing straight up
        assertEquals(-90.0, AngleMeasure.elevationFromGravity(0.0, 0.0, 9.81), 1e-9) // camera pointing at the floor
    }

    @Test fun treeHeight() {
        val r = AngleMeasure.solve(eyeHeight = 1.5, baseAngleDeg = -45.0, topAngleDeg = 45.0)
        assertEquals(1.5, r.distance!!, 1e-9)
        assertEquals(3.0, r.height!!, 1e-9)
        assertTrue(r.heightRange!!.contains(3.0))
        assertTrue(r.relativeError < 0.1)
    }

    @Test fun shallowAngleIsUnreliable() {
        // Base only 1° below horizontal: distance ~86 m, but tiny angle errors swing it wildly.
        val r = AngleMeasure.solve(1.5, -1.0, null)
        assertTrue(r.distance!! > 80)
        assertEquals(Quality.UNRELIABLE, AngleMeasure.quality(r.relativeError))
        assertNull(AngleMeasure.solve(1.5, -0.2, null).distance)
    }

    @Test fun knownDistance() {
        val r = AngleMeasure.solve(1.6, null, 30.0, knownDistance = 10.0)
        assertEquals(1.6 + 10 * Math.tan(PI / 6), r.height!!, 1e-9)
        // ±0.5° and ±1 % distance give about ±3 % on the height: within the GOOD threshold (5 %).
        assertEquals(0.030, r.relativeError, 0.003)
        assertEquals(Quality.GOOD, AngleMeasure.quality(r.relativeError))
        // Looking almost straight up (85°) the same angle error matters much more (~11 %).
        val steep = AngleMeasure.solve(1.6, null, 85.0, knownDistance = 10.0)
        assertEquals(Quality.LIMITED, AngleMeasure.quality(steep.relativeError))
    }
}

class ReferenceAndGeometryTest {
    @Test fun referenceScale() {
        assertEquals(171.2, ReferenceScale.length(85.6, 200.0, 400.0)!!, 1e-9)
        assertEquals(Quality.LIMITED, ReferenceScale.quality(200.0, 400.0))
        assertEquals(Quality.UNRELIABLE, ReferenceScale.quality(30.0, 400.0))
    }

    @Test fun shapes() {
        assertEquals(6.0, Geometry.triangle(3.0, 4.0, 5.0)!!, 1e-9)
        assertNull(Geometry.triangle(1.0, 1.0, 3.0))
        assertEquals(PI, Geometry.circle(1.0), 1e-12)
        assertEquals(10.0, Geometry.wall(4.0, 3.0, 2.0), 1e-12)
        assertEquals(4.0 / 3.0 * PI, Geometry.sphere(1.0), 1e-12)
        assertEquals(Geometry.cylinder(1.0, 3.0) / 3.0, Geometry.cone(1.0, 3.0), 1e-12)
    }
}
