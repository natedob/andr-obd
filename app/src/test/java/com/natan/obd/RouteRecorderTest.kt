package com.natan.obd

import com.natan.obd.route.GeoMath
import com.natan.obd.route.RouteRecorder
import com.natan.obd.route.SimulatedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RouteRecorderTest {
    @Test fun haversineMatchesKnownDistance() {
        // One degree of latitude is ~111.2 km.
        assertEquals(111_195.0, GeoMath.distanceMeters(50.0, 10.0, 51.0, 10.0), 200.0)
        assertEquals(0.0, GeoMath.distanceMeters(50.0, 10.0, 50.0, 10.0), 0.001)
    }

    @Test fun firstFixIsAlwaysKept() = assertTrue(RouteRecorder().add(50.0, 10.0, 0, 80))

    @Test fun fixesTooCloseInTimeOrSpaceAreDropped() {
        val r = RouteRecorder(minIntervalMs = 3000, minDistanceM = 8.0)
        r.add(50.0, 10.0, 0, 80)
        assertFalse(r.add(50.001, 10.0, 1000, 80)) // far enough, but too soon
        assertFalse(r.add(50.00001, 10.0, 5000, 80)) // late enough, but ~1 m away (parked)
        assertTrue(r.add(50.001, 10.0, 5000, 80))
        assertEquals(2, r.fixes.size)
    }

    @Test fun invalidCoordinatesAreIgnored() = assertFalse(RouteRecorder().add(Double.NaN, 10.0, 0, 80))

    @Test fun simulatedRouteMovesAsFarAsTheSpeedSays() {
        val route = SimulatedRoute(50.0, 10.0, Random(1))
        for (i in 0 until 3600) route.advance(speedKmh = 36, dtMs = 1000) // one hour at 36 km/h
        // The path wanders, so the straight-line distance is at most the 36 km travelled.
        val d = GeoMath.distanceMeters(50.0, 10.0, route.lat, route.lon)
        assertTrue("distance was $d", d in 100.0..36_500.0)
    }
}
