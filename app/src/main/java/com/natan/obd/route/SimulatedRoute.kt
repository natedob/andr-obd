package com.natan.obd.route

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A wandering path that moves exactly as far as the simulated speed says, for demo mode. */
class SimulatedRoute(
    startLat: Double,
    startLon: Double,
    private val random: Random = Random.Default,
) {
    var lat = startLat; private set
    var lon = startLon; private set
    private var headingRad = random.nextDouble(0.0, 2 * Math.PI)

    fun advance(speedKmh: Int, dtMs: Long) {
        headingRad += random.nextDouble(-0.12, 0.12) // gentle bends
        val metres = speedKmh / 3.6 * dtMs / 1000.0
        lat += metres * cos(headingRad) / METRES_PER_DEGREE
        lon += metres * sin(headingRad) / (METRES_PER_DEGREE * cos(Math.toRadians(lat)))
    }

    companion object {
        private const val METRES_PER_DEGREE = 111_320.0
        /** Used when the phone has no known position, so the demo route has somewhere to be. */
        const val FALLBACK_LAT = 50.0
        const val FALLBACK_LON = 10.0
    }
}
