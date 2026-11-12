package com.natan.obd.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteFix(val tSec: Int, val lat: Double, val lon: Double, val eco: Int)

object GeoMath {
    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Keeps a route compact: a fix is stored only if enough time AND distance passed since the last one. */
class RouteRecorder(
    private val minIntervalMs: Long = 3_000,
    private val minDistanceM: Double = 8.0,
) {
    private val _fixes = ArrayList<RouteFix>()
    private var lastAt = Long.MIN_VALUE

    val fixes: List<RouteFix> @Synchronized get() = _fixes.toList()

    @Synchronized
    fun add(lat: Double, lon: Double, atMs: Long, eco: Int): Boolean {
        if (lat.isNaN() || lon.isNaN()) return false
        val last = _fixes.lastOrNull()
        if (last != null) {
            if (atMs - lastAt < minIntervalMs) return false
            if (GeoMath.distanceMeters(last.lat, last.lon, lat, lon) < minDistanceM) return false
        }
        _fixes += RouteFix((atMs / 1000).toInt(), lat, lon, eco)
        lastAt = atMs
        return true
    }
}
