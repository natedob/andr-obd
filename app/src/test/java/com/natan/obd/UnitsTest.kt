package com.natan.obd

import com.natan.obd.ui.Units
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class UnitsTest {
    @Before fun fixLocale() = Locale.setDefault(Locale.US)

    @Test fun metricIsUnchanged() {
        val u = Units(false)
        assertEquals("100 km/h", u.speed(100))
        assertEquals("12.5 km", u.distance(12.5))
        assertEquals("6.0 L/100 km", u.consumption(6.0))
        assertEquals("90 °C", u.temperature(90))
        assertEquals("4.50 L", u.volume(4.5))
    }

    @Test fun imperialConverts() {
        val u = Units(true)
        assertEquals("62 mph", u.speed(100))
        assertEquals("7.8 mi", u.distance(12.5))
        assertEquals("39.2 mpg", u.consumption(6.0))
        assertEquals("194 °F", u.temperature(90))
        assertEquals("1.19 gal", u.volume(4.5))
        assertEquals("14 °F", u.temperature(-10))
    }

    @Test fun zeroConsumptionDoesNotDivideByZero() = assertEquals("0.0 L/100 km", Units(true).consumption(0.0))
}
