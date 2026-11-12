package com.natan.obd.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** Polls a real ELM327 adapter: one sequential loop, so the serial line is never contended. */
class ElmObdSource(
    private val client: Elm327Client,
    private val diesel: Boolean = false,
    private val pauseBetweenCyclesMs: Long = 50,
) : ObdSource {

    private companion object {
        val LAMBDA_PIDS = listOf(Pid.LAMBDA_24, Pid.LAMBDA_34)
        val VITAL_PIDS = listOf(Pid.THROTTLE, Pid.MAP, Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.OIL_TEMP, Pid.VOLTAGE, Pid.FUEL_LEVEL)
    }

    private enum class FuelMode { RATE, MAF, MAF_LAMBDA, LEVEL, NONE }

    override fun samples(): Flow<ObdSample> = flow {
        client.initialise()
        var lambdaPid: Pid? = null
        val fuelMode = when {
            client.read(Pid.FUEL_RATE) != null -> FuelMode.RATE
            // Diesel: air flow only means something together with the lambda reading. Without it we'd
            // be guessing by a factor of two, so fall back to the tank gauge instead of showing a wrong figure.
            diesel -> {
                lambdaPid = LAMBDA_PIDS.firstOrNull { client.read(it) != null }
                when {
                    lambdaPid != null && client.read(Pid.MAF) != null -> FuelMode.MAF_LAMBDA
                    client.read(Pid.FUEL_LEVEL) != null -> FuelMode.LEVEL
                    else -> FuelMode.NONE
                }
            }
            client.read(Pid.MAF) != null -> FuelMode.MAF
            client.read(Pid.FUEL_LEVEL) != null -> FuelMode.LEVEL
            else -> FuelMode.NONE
        }
        // Only poll the vitals this particular car answers.
        // In tank-gauge fuel mode that PID is already read separately below, so don't poll it twice.
        val vitals = VITAL_PIDS
            .filter { it != Pid.FUEL_LEVEL || fuelMode != FuelMode.LEVEL }
            .filter { client.read(it) != null }
        var tank: Double? = null
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
                FuelMode.MAF -> rate = client.read(Pid.MAF)?.let { ObdParsers.mafToLitresPerHour(it) }
                FuelMode.MAF_LAMBDA -> {
                    val maf = client.read(Pid.MAF)
                    val lambda = client.read(lambdaPid!!)
                    // Ignore implausible lambda (sensor warming up or switching) rather than spike the figure.
                    if (maf != null && lambda != null && lambda in 0.8..8.0) rate = ObdParsers.dieselLitresPerHour(maf, lambda)
                }
                // The tank gauge moves slowly; sampling it every ~25 cycles is plenty.
                FuelMode.LEVEL -> if (cycle % 25 == 0) {
                    level = client.read(Pid.FUEL_LEVEL)
                    level?.let { tank = it }
                }
                FuelMode.NONE -> Unit
            }

            // Vitals change slowly, so one per cycle in rotation keeps the fast loop fast.
            if (vitals.isNotEmpty()) {
                val pid = vitals[cycle % vitals.size]
                client.read(pid)?.let { vitalValues[pid] = it }
            }

            vitalValues[Pid.FUEL_LEVEL]?.let { tank = it }

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
                    tankPercent = tank,
                ),
            )
            cycle++
            delay(pauseBetweenCyclesMs)
        }
    }
}
