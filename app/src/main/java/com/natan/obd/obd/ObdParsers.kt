package com.natan.obd.obd

/** The OBD-II mode 01 PIDs we use, with their decoding formulas (SAE J1979). */
enum class Pid(val code: String, val minBytes: Int, val decode: (IntArray) -> Double) {
    MONITOR_STATUS("01", 4, { it[0].toDouble() }), // bit 7 = check-engine lamp, bits 0-6 = stored code count
    LOAD("04", 1, { it[0] * 100.0 / 255 }),
    // Wide-range O2 sensor: air/fuel equivalence ratio (lambda). 1.0 is stoichiometric, diesels run well above it.
    LAMBDA_24("24", 4, { (it[0] * 256 + it[1]) * 2.0 / 65536 }),
    LAMBDA_34("34", 4, { (it[0] * 256 + it[1]) * 2.0 / 65536 }),
    COOLANT_TEMP("05", 1, { it[0] - 40.0 }), // °C
    MAP("0B", 1, { it[0].toDouble() }), // intake manifold pressure, kPa
    INTAKE_TEMP("0F", 1, { it[0] - 40.0 }), // °C
    THROTTLE("11", 1, { it[0] * 100.0 / 255 }), // %
    VOLTAGE("42", 2, { (it[0] * 256 + it[1]) / 1000.0 }), // control module voltage, V
    OIL_TEMP("5C", 1, { it[0] - 40.0 }), // °C
    RPM("0C", 2, { (it[0] * 256 + it[1]) / 4.0 }),
    SPEED("0D", 1, { it[0].toDouble() }),
    MAF("10", 2, { (it[0] * 256 + it[1]) / 100.0 }), // g/s
    FUEL_LEVEL("2F", 1, { it[0] * 100.0 / 255 }), // %
    FUEL_RATE("5E", 2, { (it[0] * 256 + it[1]) / 20.0 }); // L/h

    val request: String get() = "01$code"
}

/** Pure string -> number logic, kept free of I/O so it can be unit tested. */
object ObdParsers {
    const val PETROL_AFR = 14.7
    const val PETROL_DENSITY_G_PER_L = 740.0
    const val DIESEL_STOICH_AFR = 14.5
    const val DIESEL_DENSITY_G_PER_L = 832.0

    /**
     * Litres/hour from a mass air flow in g/s. The default is petrol, which runs near stoichiometric,
     * so [airFuelRatio] is a constant. A diesel does not: pass its real ratio (14.5 x lambda).
     */
    fun mafToLitresPerHour(
        gramsPerSecond: Double,
        airFuelRatio: Double = PETROL_AFR,
        densityGramsPerLitre: Double = PETROL_DENSITY_G_PER_L,
    ): Double = gramsPerSecond * 3600.0 / (airFuelRatio * densityGramsPerLitre)

    fun dieselLitresPerHour(gramsPerSecond: Double, lambda: Double): Double =
        mafToLitresPerHour(gramsPerSecond, DIESEL_STOICH_AFR * lambda, DIESEL_DENSITY_G_PER_L)

    /**
     * Extracts the data bytes of a mode 01 reply for [pid], or null for "NO DATA", errors and garbage.
     * Tolerates echo, spaces, CAN headers and multi-line replies ("SEARCHING...\r41 0C 1A F8").
     */
    fun bytes(response: String, pid: Pid): IntArray? = bytes(response, pid.code)

    /** Same, for any mode 01 PID given as two hex digits, e.g. "A0". */
    fun bytes(response: String, pidCode: String): IntArray? {
        val marker = "41" + pidCode.uppercase()
        for (line in response.split('\r', '\n')) {
            val s = line.filterNot { it.isWhitespace() }.uppercase()
            val idx = s.indexOf(marker)
            if (idx < 0) continue
            val hex = s.substring(idx + marker.length)
            if (hex.isEmpty() || hex.length % 2 != 0 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) continue
            return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    fun decode(response: String, pid: Pid): Double? =
        bytes(response, pid)?.takeIf { it.size >= pid.minBytes }?.let(pid.decode)
}
