package com.natan.obd

import com.natan.obd.trip.ScoreCalculator
import com.natan.obd.trip.ScoreTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {
    private val targets = ScoreTargets(rpm = 2000, speedKmh = 80, loadPercent = 25.0, consumptionL100 = 6.0)

    @Test fun perfectTripScores100() {
        val s = ScoreCalculator.calculate(2000, 80, 25.0, 6.0, targets)
        assertEquals(100, s.total)
        assertEquals(100, s.efficiency)
    }

    @Test fun maximumIsReachable() {
        // The old formula applied the weights twice and could never exceed about 50.
        assertEquals(100, ScoreCalculator.calculate(2000, 80, 25.0, 4.0, targets).total)
    }

    @Test fun integerValuesAreNotTruncatedToZeroDifference() {
        // The old code did Int/Int division, so 50 vs 80 km/h counted as "no deviation".
        val s = ScoreCalculator.calculate(2000, 50, 25.0, 6.0, targets)
        assertEquals(63, s.speed) // 1 - 30/80 = 0.625, rounded
        assertTrue(s.total < 100)
    }

    @Test fun farOffTargetBottomsOutAtZeroNotNegative() {
        val s = ScoreCalculator.calculate(9000, 250, 100.0, 30.0, targets)
        assertEquals(0, s.speed)
        assertEquals(0, s.rpm)
        assertEquals(0, s.load)
        assertTrue(s.total in 0..100)
    }

    @Test fun highConsumptionScalesEfficiencyDown() {
        val s = ScoreCalculator.calculate(2000, 80, 25.0, 12.0, targets)
        assertEquals(50, s.efficiency)
        assertEquals(88, s.total) // 75 + 25 * 0.5 = 87.5
    }

    @Test fun missingFuelDataIsNotPunished() {
        val s = ScoreCalculator.calculate(2000, 80, 25.0, null, targets)
        assertNull(s.efficiency)
        assertEquals(100, s.total)
    }

    @Test fun zeroTargetsDoNotCrash() {
        val zero = ScoreTargets(0, 0, 0.0, 0.0)
        assertEquals(100, ScoreCalculator.calculate(2000, 80, 25.0, 5.0, zero).total)
    }

    @Test fun nonFiniteConsumptionIsIgnored() {
        assertNull(ScoreCalculator.calculate(2000, 80, 25.0, Double.NaN, targets).efficiency)
        assertNull(ScoreCalculator.calculate(2000, 80, 25.0, Double.POSITIVE_INFINITY, targets).efficiency)
    }
}
