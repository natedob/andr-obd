package com.natan.obd.trip

import kotlin.math.exp

/** Instantaneous fuel use. [litresPer100] is null when standing still (use [litresPerHour] then). */
data class Consumption(val litresPerHour: Double?, val litresPer100: Double?)

/**
 * Fuel rate divided by speed is very jumpy (it explodes as the car pulls away), so both are smoothed
 * over a few seconds first. Pure, with time passed in.
 */
class ConsumptionSmoother(private val smoothingMs: Double = 3_000.0) {
    private var lastAt = -1L
    private var rate = 0.0
    private var speed = 0.0

    fun update(rateLph: Double?, speedKmh: Int, atMs: Long): Consumption {
        if (rateLph == null) return Consumption(null, null) // car doesn't report fuel rate
        if (lastAt < 0) {
            rate = rateLph
            speed = speedKmh.toDouble()
        } else {
            val alpha = 1 - exp(-(atMs - lastAt) / smoothingMs)
            rate += (rateLph - rate) * alpha
            speed += (speedKmh - speed) * alpha
        }
        lastAt = atMs
        val per100 = if (speed >= MIN_SPEED_KMH) (rate / speed * 100).coerceAtMost(MAX_L100) else null
        return Consumption(rate, per100)
    }

    private companion object {
        const val MIN_SPEED_KMH = 5.0
        const val MAX_L100 = 99.9
    }
}
