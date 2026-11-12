package com.natan.obd.trip

/** Decides when to beep: only after speeding for [holdMs], and not again within [cooldownMs]. */
class SpeedAlertPolicy(
    private val limitKmh: Int,
    private val holdMs: Long = 2_000,
    private val cooldownMs: Long = 20_000,
) {
    private var overSince = -1L
    private var lastAlertAt = Long.MIN_VALUE

    /** True while the car is above the limit (drives the on-screen warning). */
    val isOver: Boolean get() = overSince >= 0

    /** Feed every sample; returns true exactly when a beep should sound. */
    fun update(speedKmh: Int, atMs: Long): Boolean {
        if (speedKmh <= limitKmh) {
            overSince = -1
            return false
        }
        if (overSince < 0) overSince = atMs
        val heldLongEnough = atMs - overSince >= holdMs
        val cooledDown = lastAlertAt == Long.MIN_VALUE || atMs - lastAlertAt >= cooldownMs
        if (heldLongEnough && cooledDown) {
            lastAlertAt = atMs
            return true
        }
        return false
    }
}
