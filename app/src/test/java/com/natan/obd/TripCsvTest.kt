package com.natan.obd

import com.natan.obd.data.Trip
import com.natan.obd.share.TripCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCsvTest {
    private fun trip(id: Long, startedAt: Long, fuel: Double? = 4.5, cons: Double? = 6.25) = Trip(
        id = id, driverId = 1, startedAt = startedAt, durationSec = 600, distanceKm = 12.3456, avgRpm = 2100,
        avgSpeed = 55, maxSpeed = 90, avgLoad = 31.25, fuelLitres = fuel, consumptionL100 = cons, score = 82,
        speedScore = 70, rpmScore = 90, loadScore = 80, efficiencyScore = 95,
    )

    @Test fun headerAndOneRow() {
        val csv = TripCsv.build(listOf(trip(1, 0)), mapOf(1L to "Ann")).lines()
        assertEquals(TripCsv.HEADER, csv[0])
        assertEquals("1970-01-01T00:00:00Z,Ann,12.346,600,55,90,2100,31.3,4.500,6.25,82", csv[1])
    }

    @Test fun missingFuelLeavesEmptyCells() {
        val row = TripCsv.build(listOf(trip(1, 0, fuel = null, cons = null)), mapOf(1L to "Ann")).lines()[1]
        assertTrue(row.endsWith(",31.3,,,82"))
    }

    @Test fun tripsAreSortedOldestFirst() {
        val lines = TripCsv.build(listOf(trip(2, 2_000_000), trip(1, 1_000_000)), mapOf(1L to "A")).lines()
        assertTrue(lines[1] < lines[2]) // ISO timestamps sort chronologically
    }

    @Test fun namesWithCommasAndQuotesAreQuoted() {
        assertEquals("\"Smith, John\"", TripCsv.escape("Smith, John"))
        assertEquals("\"say \"\"hi\"\"\"", TripCsv.escape("say \"hi\""))
        assertEquals("plain", TripCsv.escape("plain"))
    }

    @Test fun formulaInjectionIsDefused() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, TripCsv.escape("=HYPERLINK(\"x\")"))
        assertEquals("'+1", TripCsv.escape("+1"))
    }
}
