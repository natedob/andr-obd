package com.natan.obd

import com.natan.obd.trip.ConsumptionSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumptionSmootherTest {
    @Test fun steadyCruiseConvertsRateToLitresPer100() {
        val s = ConsumptionSmoother()
        var c = s.update(6.0, 60, 0)
        for (t in 1..20) c = s.update(6.0, 60, t * 1000L)
        assertEquals(10.0, c.litresPer100!!, 0.01) // 6 L/h at 60 km/h
        assertEquals(6.0, c.litresPerHour!!, 0.01)
    }

    @Test fun standingStillOnlyGivesLitresPerHour() {
        val s = ConsumptionSmoother()
        var c = s.update(0.8, 0, 0)
        for (t in 1..10) c = s.update(0.8, 0, t * 1000L)
        assertNull(c.litresPer100)
        assertEquals(0.8, c.litresPerHour!!, 0.01)
    }

    @Test fun missingFuelRateGivesNothing() {
        val c = ConsumptionSmoother().update(null, 80, 0)
        assertNull(c.litresPerHour)
        assertNull(c.litresPer100)
    }

    @Test fun pullingAwayDoesNotProduceAbsurdFigures() {
        val s = ConsumptionSmoother()
        s.update(0.8, 0, 0)
        // Full throttle from standstill: 25 L/h at 6 km/h would be over 400 L/100 km unsmoothed.
        val c = s.update(25.0, 6, 1000)
        assertNotNull(c.litresPerHour)
        assertTrue("was ${c.litresPer100}", c.litresPer100 == null || c.litresPer100!! <= 99.9)
    }

    @Test fun readingsAreSmoothedNotJumpy() {
        val s = ConsumptionSmoother()
        for (t in 0..10) s.update(5.0, 50, t * 1000L)
        val c = s.update(20.0, 50, 11_000) // one sudden spike
        assertTrue(c.litresPerHour!! < 12.0) // pulled only part of the way towards 20
    }
}
