package com.natan.obd.share

import com.natan.obd.data.Trip
import java.time.Instant
import java.util.Locale

/** CSV is always metric and locale-independent, so spreadsheets parse it the same everywhere. */
object TripCsv {
    const val HEADER =
        "date,driver,distance_km,duration_s,avg_speed_kmh,max_speed_kmh,avg_rpm,avg_load_pct,fuel_l,consumption_l_per_100km,score"

    fun build(trips: List<Trip>, driverNames: Map<Long, String>): String = buildString {
        appendLine(HEADER)
        for (t in trips.sortedBy { it.startedAt }) {
            appendLine(
                listOf(
                    Instant.ofEpochMilli(t.startedAt).toString(),
                    escape(driverNames[t.driverId].orEmpty()),
                    num(t.distanceKm, 3), t.durationSec.toString(), t.avgSpeed.toString(), t.maxSpeed.toString(),
                    t.avgRpm.toString(), num(t.avgLoad, 1),
                    t.fuelLitres?.let { num(it, 3) }.orEmpty(),
                    t.consumptionL100?.let { num(it, 2) }.orEmpty(),
                    t.score.toString(),
                ).joinToString(","),
            )
        }
    }

    /** Quotes fields containing commas, quotes or newlines; and defuses spreadsheet formula injection. */
    fun escape(field: String): String {
        val safe = if (field.firstOrNull() in setOf('=', '+', '-', '@')) "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun num(v: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)
}
