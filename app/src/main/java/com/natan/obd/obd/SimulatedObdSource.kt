package com.natan.obd.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.random.Random

/** A believable fake drive for demo mode and development: it accelerates, cruises and brakes. */
class SimulatedObdSource(
    private val random: Random = Random.Default,
    private val periodMs: Long = 200,
) : ObdSource {

    override fun samples(): Flow<ObdSample> = flow {
        var speed = 0.0
        var target = 40.0
        var tick = 0
        while (true) {
            if (tick % 50 == 0) target = if (random.nextInt(8) == 0) 0.0 else random.nextDouble(25.0, 115.0)
            val before = speed
            speed += (target - speed).coerceIn(-3.0, 1.6)
            val accel = speed - before

            val rpm = (850 + speed * 24 + max(0.0, accel) * 120 + random.nextDouble(-40.0, 40.0)).coerceIn(700.0, 6500.0)
            val load = (18 + speed * 0.28 + max(0.0, accel) * 14 + random.nextDouble(-2.0, 2.0)).coerceIn(5.0, 95.0)
            val fuelRate = 0.8 + load * 0.075 + speed * 0.01

            emit(ObdSample(rpm.toInt(), speed.toInt(), load, fuelRateLph = fuelRate))
            tick++
            delay(periodMs)
        }
    }
}
