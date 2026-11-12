package com.natan.obd.trip

import kotlin.math.abs
import kotlin.math.roundToInt

/** The driver's "ideal" values; the score measures how closely a trip stays to them. */
data class ScoreTargets(
    val rpm: Int,
    val speedKmh: Int,
    val loadPercent: Double,
    val consumptionL100: Double,
)

/** Every component is 0..100 (100 = right on target). [efficiency] is null without fuel data. */
data class ScoreBreakdown(val total: Int, val speed: Int, val rpm: Int, val load: Int, val efficiency: Int?)

object ScoreCalculator {
    private const val ECO_WEIGHT = 0.75
    private const val EFFICIENCY_WEIGHT = 0.25

    /**
     * Eco part (75%): speed 30% + RPM 40% + load 30%, each as closeness to its target.
     * Efficiency part (25%): full marks at or below the target consumption, scaled down above it.
     * Without consumption data the eco part alone is scaled to 100, so nobody is punished for
     * driving a car that doesn't report fuel.
     */
    fun calculate(
        avgRpm: Int,
        avgSpeedKmh: Int,
        avgLoadPercent: Double,
        consumptionL100: Double?,
        targets: ScoreTargets,
    ): ScoreBreakdown {
        val speed = closeness(avgSpeedKmh.toDouble(), targets.speedKmh.toDouble())
        val rpm = closeness(avgRpm.toDouble(), targets.rpm.toDouble())
        val load = closeness(avgLoadPercent, targets.loadPercent)
        val eco = 0.30 * speed + 0.40 * rpm + 0.30 * load

        val efficiency = consumptionL100
            ?.takeIf { it.isFinite() && it >= 0 }
            ?.let { if (targets.consumptionL100 <= 0.0 || it <= targets.consumptionL100) 1.0 else targets.consumptionL100 / it }

        val total = if (efficiency != null) ECO_WEIGHT * eco + EFFICIENCY_WEIGHT * efficiency else eco
        return ScoreBreakdown(
            total = percent(total),
            speed = percent(speed),
            rpm = percent(rpm),
            load = percent(load),
            efficiency = efficiency?.let(::percent),
        )
    }

    /** 1.0 on target, falling linearly to 0.0 when off by 100% of the target or more. */
    internal fun closeness(actual: Double, optimal: Double): Double =
        if (optimal <= 0.0) 1.0 else (1.0 - abs(actual - optimal) / optimal).coerceIn(0.0, 1.0)

    private fun percent(fraction: Double) = (fraction * 100).roundToInt().coerceIn(0, 100)
}
