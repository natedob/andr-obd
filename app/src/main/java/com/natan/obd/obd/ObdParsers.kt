package com.natan.obd.obd

/** The OBD-II mode 01 PIDs we use, with their decoding formulas (SAE J1979). */
enum class Pid(val code: String, val minBytes: Int, val decode: (IntArray) -> Double) {
    LOAD("04", 1, { it[0] * 100.0 / 255 }),
    RPM("0C", 2, { (it[0] * 256 + it[1]) / 4.0 }),
    SPEED("0D", 1, { it[0].toDouble() }),
    MAF("10", 2, { (it[0] * 256 + it[1]) / 100.0 }), // g/s
    FUEL_LEVEL("2F", 1, { it[0] * 100.0 / 255 }), // %
    FUEL_RATE("5E", 2, { (it[0] * 256 + it[1]) / 20.0 }); // L/h

    val request: String get() = "01$code"
}

/** Pure string -> number logic, kept free of I/O so it can be unit tested. */
object ObdParsers {
    /** Litres/hour of petrol for a mass air flow in g/s, assuming a stoichiometric 14.7:1 mixture. */
    fun mafToLitresPerHour(gramsPerSecond: Double): Double = gramsPerSecond * 3600.0 / (14.7 * 740.0)

    /**
     * Extracts the data bytes of a mode 01 reply for [pid], or null for "NO DATA", errors and garbage.
     * Tolerates echo, spaces, CAN headers and multi-line replies ("SEARCHING...\r41 0C 1A F8").
     */
    fun bytes(response: String, pid: Pid): IntArray? {
        val marker = "41" + pid.code
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
