package com.natan.obd.obd

/** How fuel consumption can be worked out on a given car, best first. */
enum class FuelMethod { DIRECT_RATE, MAF_ESTIMATE, MAF_LAMBDA_ESTIMATE, TANK_GAUGE_ONLY, NONE }

/** Pure helpers for the "which readings does my car support" feature. */
object SupportedPids {
    /**
     * Decodes one 4-byte support mask. Bit 31 (the top bit of the first byte) is PID base+1, and
     * the last bit is PID base+32, which says whether the NEXT range can be queried.
     */
    fun parseMask(bytes: IntArray, base: Int): Set<Int> {
        if (bytes.size < 4) return emptySet()
        val mask = bytes.take(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        return (0 until 32).filter { bit -> mask and (1L shl (31 - bit)) != 0L }.map { base + 1 + it }.toSet()
    }

    /** PIDs that only announce the next range; not interesting to show. */
    fun isRangeMarker(pid: Int) = pid != 0 && pid % 0x20 == 0

    fun name(pid: Int): String? = NAMES[pid]

    /** Mirrors what the app actually does at the start of a trip (see ElmObdSource). */
    fun fuelMethod(supported: Set<Int>, diesel: Boolean): FuelMethod = when {
        0x5E in supported -> FuelMethod.DIRECT_RATE
        diesel -> when {
            0x10 in supported && (0x24 in supported || 0x34 in supported) -> FuelMethod.MAF_LAMBDA_ESTIMATE
            0x2F in supported -> FuelMethod.TANK_GAUGE_ONLY
            else -> FuelMethod.NONE
        }
        0x10 in supported -> FuelMethod.MAF_ESTIMATE
        0x2F in supported -> FuelMethod.TANK_GAUGE_ONLY
        else -> FuelMethod.NONE
    }

    private val NAMES: Map<Int, String> = buildMap {
        put(0x01, "Monitor status since codes cleared"); put(0x02, "Freeze-frame trouble code")
        put(0x03, "Fuel system status"); put(0x04, "Calculated engine load"); put(0x05, "Coolant temperature")
        put(0x06, "Short-term fuel trim (bank 1)"); put(0x07, "Long-term fuel trim (bank 1)")
        put(0x08, "Short-term fuel trim (bank 2)"); put(0x09, "Long-term fuel trim (bank 2)")
        put(0x0A, "Fuel pressure"); put(0x0B, "Intake manifold pressure"); put(0x0C, "Engine speed")
        put(0x0D, "Vehicle speed"); put(0x0E, "Timing advance"); put(0x0F, "Intake air temperature")
        put(0x10, "Air flow rate (MAF)"); put(0x11, "Throttle position"); put(0x12, "Secondary air status")
        put(0x13, "Oxygen sensors present"); put(0x1C, "OBD standard the car conforms to")
        put(0x1D, "Oxygen sensors present (4 banks)"); put(0x1E, "Auxiliary input status")
        put(0x1F, "Run time since engine start")
        for (i in 0..7) put(0x14 + i, "Oxygen sensor ${i + 1} (voltage)")
        for (i in 0..7) put(0x24 + i, "Oxygen sensor ${i + 1} (lambda, voltage)")
        for (i in 0..7) put(0x34 + i, "Oxygen sensor ${i + 1} (lambda, current)")
        put(0x21, "Distance with check-engine light on"); put(0x22, "Fuel rail pressure (relative)")
        put(0x23, "Fuel rail pressure (direct injection)"); put(0x2C, "Commanded EGR"); put(0x2D, "EGR error")
        put(0x2E, "Commanded evaporative purge"); put(0x2F, "Fuel tank level"); put(0x30, "Warm-ups since codes cleared")
        put(0x31, "Distance since codes cleared"); put(0x32, "Evap system vapour pressure")
        put(0x33, "Barometric pressure"); put(0x3C, "Catalyst temperature (bank 1, sensor 1)")
        put(0x3D, "Catalyst temperature (bank 2, sensor 1)"); put(0x3E, "Catalyst temperature (bank 1, sensor 2)")
        put(0x3F, "Catalyst temperature (bank 2, sensor 2)")
        put(0x41, "Monitor status this drive cycle"); put(0x42, "Battery / control module voltage")
        put(0x43, "Absolute engine load"); put(0x44, "Commanded air-fuel ratio (lambda)")
        put(0x45, "Relative throttle position"); put(0x46, "Ambient air temperature")
        put(0x47, "Absolute throttle position B"); put(0x48, "Absolute throttle position C")
        put(0x49, "Accelerator pedal position D"); put(0x4A, "Accelerator pedal position E")
        put(0x4B, "Accelerator pedal position F"); put(0x4C, "Commanded throttle actuator")
        put(0x4D, "Time with check-engine light on"); put(0x4E, "Time since codes cleared")
        put(0x51, "Fuel type"); put(0x52, "Ethanol fuel percentage"); put(0x53, "Absolute evap vapour pressure")
        put(0x59, "Fuel rail pressure (absolute)"); put(0x5A, "Relative accelerator pedal position")
        put(0x5B, "Hybrid battery remaining life"); put(0x5C, "Engine oil temperature")
        put(0x5D, "Fuel injection timing"); put(0x5E, "Engine fuel rate"); put(0x5F, "Emission requirements")
        put(0x61, "Driver demand torque"); put(0x62, "Actual engine torque"); put(0x63, "Engine reference torque")
        put(0x64, "Engine torque data"); put(0x65, "Auxiliary input/output"); put(0x66, "Air flow sensor")
        put(0x67, "Coolant temperature (sensors)"); put(0x68, "Intake air temperature (sensors)")
        put(0x69, "Commanded EGR and EGR error"); put(0x6A, "Commanded diesel intake air flow")
        put(0x6B, "EGR temperature"); put(0x6C, "Commanded throttle actuator control")
        put(0x6D, "Fuel pressure control"); put(0x6E, "Injection pressure control")
        put(0x6F, "Turbo compressor inlet pressure"); put(0x70, "Boost pressure")
        put(0x71, "Variable-geometry turbo control"); put(0x72, "Wastegate control"); put(0x73, "Exhaust pressure")
        put(0x74, "Turbocharger speed"); put(0x75, "Turbocharger temperature"); put(0x76, "Turbocharger temperature (2)")
        put(0x77, "Charge air cooler temperature"); put(0x78, "Exhaust gas temperature (bank 1)")
        put(0x79, "Exhaust gas temperature (bank 2)"); put(0x7A, "Diesel particulate filter (bank 1)")
        put(0x7B, "Diesel particulate filter (bank 2)"); put(0x7C, "Diesel particulate filter temperature")
        put(0x7D, "NOx control area status"); put(0x7E, "Particulate control area status"); put(0x7F, "Engine run time")
    }
}
