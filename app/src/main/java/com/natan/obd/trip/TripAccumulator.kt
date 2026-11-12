package com.natan.obd.trip

import com.natan.obd.obd.ObdSample
import kotlin.math.min

data class TripPoint(val tSec: Int, val rpm: Int, val speed: Int, val load: Double)

data class TripStats(
    val durationMs: Long,
    val distanceKm: Double,
    val avgRpm: Int,
    val avgSpeed: Int,
    val maxSpeed: Int,
    val avgLoad: Double,
    val fuelLitres: Double?,
)

/**
 * Turns a stream of samples into trip statistics. Pure (timestamps are passed in), so it's testable.
 * Averages are time-weighted, distance is integrated from speed, and fuel comes from the fuel-rate
 * integral when available, otherwise from the drop in tank level.
 */
class TripAccumulator(
    private val tankLitres: Double,
    private val pointIntervalMs: Long = 2_000,
) {
    private var firstAt = -1L
    private var lastAt = -1L
    private var weightedMs = 0L
    private var rpmSum = 0.0
    private var speedSum = 0.0
    private var loadSum = 0.0
    private var distanceKm = 0.0
    private var maxSpeed = 0

    private var rateLitres = 0.0
    private var rateMs = 0L

    private val firstLevels = ArrayList<Double>()
    private val lastLevels = ArrayDeque<Double>()
    private var levelCount = 0

    private val _points = ArrayList<TripPoint>()
    private var lastPointAt = Long.MIN_VALUE
    val points: List<TripPoint> get() = _points

    fun add(sample: ObdSample, atMs: Long) {
        if (firstAt < 0) firstAt = atMs
        if (lastAt >= 0) {
            // Cap the interval so a stall in the data doesn't get counted as driving time.
            val dt = min(atMs - lastAt, MAX_GAP_MS)
            weightedMs += dt
            rpmSum += sample.rpm * dt.toDouble()
            speedSum += sample.speedKmh * dt.toDouble()
            loadSum += sample.loadPercent * dt.toDouble()
            distanceKm += sample.speedKmh * dt / 3_600_000.0
            sample.fuelRateLph?.let {
                rateLitres += it * dt / 3_600_000.0
                rateMs += dt
            }
        }
        lastAt = atMs
        if (sample.speedKmh > maxSpeed) maxSpeed = sample.speedKmh

        sample.fuelLevelPercent?.let {
            levelCount++
            if (firstLevels.size < LEVEL_WINDOW) firstLevels += it
            lastLevels.addLast(it)
            if (lastLevels.size > LEVEL_WINDOW) lastLevels.removeFirst()
        }

        if (lastPointAt == Long.MIN_VALUE || atMs - lastPointAt >= pointIntervalMs) {
            _points += TripPoint(((atMs - firstAt) / 1000).toInt(), sample.rpm, sample.speedKmh, sample.loadPercent)
            lastPointAt = atMs
        }
    }

    fun snapshot(): TripStats {
        val w = weightedMs.toDouble()
        return TripStats(
            durationMs = if (firstAt < 0) 0 else lastAt - firstAt,
            distanceKm = distanceKm,
            avgRpm = if (weightedMs > 0) (rpmSum / w).toInt() else 0,
            avgSpeed = if (weightedMs > 0) (speedSum / w).toInt() else 0,
            maxSpeed = maxSpeed,
            avgLoad = if (weightedMs > 0) loadSum / w else 0.0,
            fuelLitres = fuelLitres(),
        )
    }

    private fun fuelLitres(): Double? {
        if (weightedMs > 0 && rateMs >= weightedMs * 0.8) return rateLitres
        // Tank-gauge fallback: only trust it when the first and last windows don't overlap
        // and the level actually moved, since the sensor is coarse (~0.4% steps) and noisy.
        if (levelCount >= 2 * LEVEL_WINDOW) {
            val drop = firstLevels.average() - lastLevels.average()
            if (drop >= MIN_LEVEL_DROP_PERCENT) return drop / 100.0 * tankLitres
        }
        return null
    }

    private companion object {
        const val MAX_GAP_MS = 2_000L
        const val LEVEL_WINDOW = 5
        const val MIN_LEVEL_DROP_PERCENT = 0.5
    }
}
