package com.natan.obd

import com.natan.obd.trip.CoachingAdvisor
import com.natan.obd.trip.Hint
import com.natan.obd.trip.ScoreTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingAdvisorTest {
    private val targets = ScoreTargets(rpm = 2000, speedKmh = 80, loadPercent = 25.0, consumptionL100 = 6.0)

    private fun CoachingAdvisor.run(seconds: Int, rpm: Int, speed: Int, load: Double, from: Long = 0): com.natan.obd.trip.Coaching {
        var last = com.natan.obd.trip.Coaching()
        for (s in 0..seconds) last = update(rpm, speed, load, from + s * 1000L)
        return last
    }

    @Test fun drivingOnTargetIsGood() {
        val c = CoachingAdvisor(targets).run(10, 2000, 80, 25.0)
        assertEquals(Hint.GOOD, c.hint)
        assertEquals(100, c.ecoNow)
    }

    @Test fun speedingTellsYouToSlowDown() =
        assertEquals(Hint.SLOW_DOWN, CoachingAdvisor(targets).run(10, 2200, 110, 25.0).hint)

    @Test fun highRevsSuggestShiftingUp() =
        assertEquals(Hint.SHIFT_UP, CoachingAdvisor(targets).run(10, 3500, 60, 25.0).hint)

    @Test fun heavyLoadSuggestsEasingTheThrottle() =
        assertEquals(Hint.EASE_THROTTLE, CoachingAdvisor(targets).run(10, 2000, 75, 60.0).hint)

    @Test fun longStopSuggestsSwitchingOff() {
        val advisor = CoachingAdvisor(targets)
        assertEquals(Hint.NONE, advisor.run(30, 800, 0, 10.0).hint) // 30 s: still fine
        assertEquals(Hint.IDLING, advisor.run(40, 800, 0, 10.0, from = 31_000).hint) // past 60 s
    }

    @Test fun aSingleSpikeDoesNotTriggerAHint() {
        val advisor = CoachingAdvisor(targets)
        advisor.run(10, 2000, 80, 25.0)
        val c = advisor.update(2000, 200, 25.0, 11_000) // one wild sample
        assertEquals(Hint.GOOD, c.hint)
    }

    @Test fun hintStaysVisibleBrieflyThenRelaxes() {
        val advisor = CoachingAdvisor(targets)
        advisor.run(10, 2200, 110, 25.0)
        // Back to good driving: the warning is held for at least 3 s, then gives way.
        assertEquals(Hint.SLOW_DOWN, advisor.update(2000, 80, 25.0, 11_000).hint)
        assertEquals(Hint.GOOD, advisor.run(15, 2000, 80, 25.0, from = 12_000).hint)
    }

    @Test fun ecoNowStaysInRange() {
        val c = CoachingAdvisor(targets).run(5, 9000, 250, 100.0)
        assertTrue(c.ecoNow in 0..100)
    }
}
