package com.natan.obd.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** Polls a real ELM327 adapter: one sequential loop, so the serial line is never contended. */
class ElmObdSource(
    private val client: Elm327Client,
    private val pauseBetweenCyclesMs: Long = 50,
) : ObdSource {

    private companion object {
        val VITAL_PIDS = listOf(Pid.THROTTLE, Pid.MAP, Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.OIL_TEMP, Pid.VOLTAGE)
    }

    private enum class FuelMode { RATE, MAF, LEVEL, NONE }

    override fun samples(): Flow<ObdSample> = flow {
        client.initialise()
        val fuelMode = detectFuelMode()
        // Only poll the vitals this particular car answers.
        val vitals = VITAL_PIDS.filter { client.read(it) != null }
        val vitalValues = HashMap<Pid, Double>()

        var rpm = 0
        var speed = 0
        var load = 0.0
        var failedCycles = 0
        var cycle = 0
        while (true) {
            var answered = false
            client.read(Pid.RPM)?.let { rpm = it.toInt(); answered = true }
            client.read(Pid.SPEED)?.let { speed = it.toInt(); answered = true }
            client.read(Pid.LOAD)?.let { load = it; answered = true }

            var rate: Double? = null
            var level: Double? = null
            when (fuelMode) {
                FuelMode.RATE -> rate = client.read(Pid.FUEL_RATE)
                FuelMode.MAF -> rate = client.read(Pid.MAF)?.let(ObdParsers::mafToLitresPerHour)
                // The tank gauge moves slowly; sampling it every ~25 cycles is plenty.
                FuelMode.LEVEL -> if (cycle % 25 == 0) level = client.read(Pid.FUEL_LEVEL)
                FuelMode.NONE -> Unit
            }

            // Vitals change slowly, so one per cycle in rotation keeps the fast loop fast.
            if (vitals.isNotEmpty()) {
                val pid = vitals[cycle % vitals.size]
                client.read(pid)?.let { vitalValues[pid] = it }
            }

            // A single "NO DATA" is normal; a long run of them means the link is dead.
            failedCycles = if (answered) 0 else failedCycles + 1
            if (failedCycles >= 10) throw IOException("Lost contact with the car")

            emit(
                ObdSample(
                    rpm, speed, load, rate, level,
                    coolantC = vitalValues[Pid.COOLANT_TEMP]?.toInt(),
                    intakeC = vitalValues[Pid.INTAKE_TEMP]?.toInt(),
                    oilC = vitalValues[Pid.OIL_TEMP]?.toInt(),
                    throttlePercent = vitalValues[Pid.THROTTLE],
                    manifoldKpa = vitalValues[Pid.MAP]?.toInt(),
                    voltage = vitalValues[Pid.VOLTAGE],
                ),
            )
            cycle++
            delay(pauseBetweenCyclesMs)
        }
    }

    /** Prefer a direct fuel-rate PID, then derive it from MAF, then fall back to the tank gauge. */
    private suspend fun detectFuelMode(): FuelMode = when {
        client.read(Pid.FUEL_RATE) != null -> FuelMode.RATE
        client.read(Pid.MAF) != null -> FuelMode.MAF
        client.read(Pid.FUEL_LEVEL) != null -> FuelMode.LEVEL
        else -> FuelMode.NONE
    }
}
