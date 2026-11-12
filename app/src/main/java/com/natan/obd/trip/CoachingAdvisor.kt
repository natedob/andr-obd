package com.natan.obd.trip

import kotlin.math.exp
import kotlin.math.roundToInt

enum class Hint(internal val severity: Int) {
    NONE(0), GOOD(1), IDLING(2), SLOW_DOWN(3), SHIFT_UP(3), EASE_THROTTLE(3)
}

/** [ecoNow] is the eco score (0..100) of the current driving, not the trip so far. */
data class Coaching(val hint: Hint = Hint.NONE, val ecoNow: Int = 0)

/**
 * Turns live readings into one calm, actionable hint. Values are smoothed (so a single spike
 * doesn't trigger anything) and a hint stays on screen for a few seconds (so the text doesn't flicker).
 * Stateful but pure: time is passed in, which keeps it testable.
 */
class CoachingAdvisor(private val targets: ScoreTargets) {
    private var lastAt = -1L
    private var rpm = 0.0
    private var speed = 0.0
    private var load = 0.0
    private var stoppedSince = -1L
    private var current = Hint.NONE
    private var currentSince = 0L
    private var pending = Hint.NONE
    private var pendingSince = 0L

    fun update(rpmNow: Int, speedNow: Int, loadNow: Double, atMs: Long): Coaching {
        if (lastAt < 0) {
            rpm = rpmNow.toDouble(); speed = speedNow.toDouble(); load = loadNow
            pendingSince = atMs
            currentSince = atMs
        } else {
            val alpha = 1 - exp(-(atMs - lastAt) / SMOOTHING_MS)
            rpm += (rpmNow - rpm) * alpha
            speed += (speedNow - speed) * alpha
            load += (loadNow - load) * alpha
        }
        lastAt = atMs

        if (speedNow == 0) { if (stoppedSince < 0) stoppedSince = atMs } else stoppedSince = -1

        val speedC = ScoreCalculator.closeness(speed, targets.speedKmh.toDouble())
        val rpmC = ScoreCalculator.closeness(rpm, targets.rpm.toDouble())
        val loadC = ScoreCalculator.closeness(load, targets.loadPercent)

        val candidate = when {
            speed > targets.speedKmh * 1.15 -> Hint.SLOW_DOWN
            rpm > targets.rpm * 1.3 && speed > 10 -> Hint.SHIFT_UP
            load > targets.loadPercent * 1.6 -> Hint.EASE_THROTTLE
            stoppedSince >= 0 && atMs - stoppedSince >= IDLE_MS -> Hint.IDLING
            speedC >= GOOD && rpmC >= GOOD && loadC >= GOOD -> Hint.GOOD
            else -> Hint.NONE
        }
        // A condition must persist briefly before it counts (sensor glitches shouldn't nag) ...
        if (candidate != pending) {
            pending = candidate
            pendingSince = atMs
        }
        val confirmed = atMs - pendingSince >= CONFIRM_MS
        // ... and, once shown, a hint stays a few seconds unless something more serious comes up.
        if (confirmed && pending != current && (pending.severity > current.severity || atMs - currentSince >= MIN_HOLD_MS)) {
            current = pending
            currentSince = atMs
        }
        val eco = 0.3 * speedC + 0.4 * rpmC + 0.3 * loadC
        return Coaching(current, (eco * 100).roundToInt())
    }

    private companion object {
        const val SMOOTHING_MS = 2_000.0
        const val CONFIRM_MS = 1_500L
        const val MIN_HOLD_MS = 3_000L
        const val IDLE_MS = 60_000L
        const val GOOD = 0.75
    }
}
