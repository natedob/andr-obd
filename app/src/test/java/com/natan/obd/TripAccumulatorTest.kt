package com.natan.obd

import com.natan.obd.obd.ObdSample
import com.natan.obd.trip.TripAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripAccumulatorTest {
    private fun feed(acc: TripAccumulator, seconds: Int, sample: (Int) -> ObdSample) {
        for (s in 0..seconds) acc.add(sample(s), s * 1000L)
    }

    @Test fun distanceIsIntegratedFromSpeed() {
        val acc = TripAccumulator(50.0)
        feed(acc, 3600) { ObdSample(2000, 60, 30.0) } // one hour at 60 km/h
        assertEquals(60.0, acc.snapshot().distanceKm, 0.01)
    }

    @Test fun averagesAreTimeWeighted() {
        val acc = TripAccumulator(50.0)
        // 10 s standing still, then 30 s at 100 km/h: the average must be ~77, not the 50 a plain mean of the two phases gives.
        feed(acc, 39) { t -> if (t < 10) ObdSample(800, 0, 10.0) else ObdSample(3000, 100, 40.0) }
        val stats = acc.snapshot()
        assertEquals(100, stats.maxSpeed)
        assertEquals(76, stats.avgSpeed)
    }

    @Test fun gapsInDataAreNotCountedAsDrivingTime() {
        val acc = TripAccumulator(50.0)
        acc.add(ObdSample(2000, 100, 30.0), 0)
        acc.add(ObdSample(2000, 100, 30.0), 600_000) // 10 minute silence
        assertEquals(100 * 2.0 / 3600, acc.snapshot().distanceKm, 0.0001) // capped to 2 s
    }

    @Test fun fuelRateIsIntegrated() {
        val acc = TripAccumulator(50.0)
        feed(acc, 3600) { ObdSample(2000, 60, 30.0, fuelRateLph = 5.0) }
        assertEquals(5.0, acc.snapshot().fuelLitres!!, 0.01)
    }

    @Test fun fuelFallsBackToTankLevelDrop() {
        val acc = TripAccumulator(tankLitres = 50.0)
        // 20 level readings falling from 80% to 70%: 10% of 50 L = 5 L.
        for (i in 0 until 20) acc.add(ObdSample(2000, 60, 30.0, fuelLevelPercent = 80.0 - i * (10.0 / 19)), i * 5_000L)
        assertEquals(4.0, acc.snapshot().fuelLitres!!, 1.0) // window averages shave the ends
        assertNotNull(acc.snapshot().fuelLitres)
    }

    @Test fun tooFewOrTooSmallLevelChangesGiveNoFuelFigure() {
        val few = TripAccumulator(50.0)
        for (i in 0 until 4) few.add(ObdSample(2000, 60, 30.0, fuelLevelPercent = 80.0 - i), i * 5_000L)
        assertNull(few.snapshot().fuelLitres)

        val flat = TripAccumulator(50.0)
        for (i in 0 until 20) flat.add(ObdSample(2000, 60, 30.0, fuelLevelPercent = 80.0), i * 5_000L)
        assertNull(flat.snapshot().fuelLitres)
    }

    @Test fun noSamplesGivesZeroedStats() {
        val s = TripAccumulator(50.0).snapshot()
        assertEquals(0, s.durationMs)
        assertEquals(0.0, s.distanceKm, 0.0)
        assertNull(s.fuelLitres)
    }

    @Test fun chartPointsAreDownsampled() {
        val acc = TripAccumulator(50.0, pointIntervalMs = 2_000)
        feed(acc, 20) { ObdSample(2000, 50, 20.0) }
        assertEquals(11, acc.points.size) // one point every 2 s over 20 s
    }
}
