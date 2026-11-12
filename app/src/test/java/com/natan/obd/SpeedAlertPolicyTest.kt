package com.natan.obd

import com.natan.obd.trip.SpeedAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedAlertPolicyTest {
    @Test fun noAlertAtOrBelowTheLimit() {
        val p = SpeedAlertPolicy(100)
        for (t in 0..20) assertFalse(p.update(100, t * 1000L))
        assertFalse(p.isOver)
    }

    @Test fun alertsOnlyAfterSpeedingForTheHoldTime() {
        val p = SpeedAlertPolicy(100, holdMs = 2000)
        assertFalse(p.update(120, 0))
        assertFalse(p.update(120, 1000))
        assertTrue(p.update(120, 2000))
        assertTrue(p.isOver)
    }

    @Test fun briefOvershootNeverAlerts() {
        val p = SpeedAlertPolicy(100, holdMs = 2000)
        assertFalse(p.update(120, 0))
        assertFalse(p.update(95, 1000)) // dropped below: timer resets
        assertFalse(p.update(120, 2000))
        assertFalse(p.update(120, 3000))
    }

    @Test fun cooldownPreventsNagging() {
        val p = SpeedAlertPolicy(100, holdMs = 1000, cooldownMs = 20_000)
        var beeps = 0
        for (t in 0..60) if (p.update(130, t * 1000L)) beeps++
        assertEquals(3, beeps) // at 1 s, 21 s, 41 s (and 61 s is outside the loop)
    }
}
