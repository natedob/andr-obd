package com.natan.obd.obd

/** Decodes the replies to OBD modes 03 (stored codes) and 07 (pending codes). Pure, so it's testable. */
object DtcParser {
    private val lengthLine = Regex("^[0-9A-F]{3}$")
    private val numberedLine = Regex("^[0-9A-F]:")

    /** Two bytes -> "P0133". Top two bits pick the system (P/C/B/U), the rest is the number. */
    fun decode(hi: Int, lo: Int): String {
        val system = "PCBU"[(hi shr 6) and 0x3]
        val digit = (hi shr 4) and 0x3
        val rest = ((hi and 0xF) shl 8) or lo
        return "%c%d%03X".format(system, digit, rest)
    }

    /**
     * Codes in a reply that starts with [replyMode] ("43" for stored, "47" for pending).
     * Handles padding, several ECUs answering on separate lines, a leading code-count byte (CAN) and
     * multi-frame CAN replies ("00A" / "0: 43 ..." / "1: ...").
     */
    fun parse(response: String, replyMode: String = "43"): List<String> {
        val lines = response.split('\r', '\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val numbered = lines.filter { numberedLine.containsMatchIn(it) }
        val declaredLength = lines.firstOrNull { lengthLine.matches(it) }?.toInt(16)

        val payloads = if (numbered.isNotEmpty()) {
            listOf(numbered.joinToString("") { it.substringAfter(':') })
        } else lines

        val codes = LinkedHashSet<String>()
        for (payload in payloads) {
            val hex = payload.filterNot { it.isWhitespace() }
            if (!hex.startsWith(replyMode) || hex.length % 2 != 0 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) continue
            var bytes = hex.chunked(2).map { it.toInt(16) }
            if (numbered.isNotEmpty() && declaredLength != null) bytes = bytes.take(declaredLength)
            var data = bytes.drop(1) // drop the mode byte
            if (data.size % 2 == 1) data = data.drop(1) // CAN adds a count byte, leaving an odd length
            for (i in 0 until data.size - 1 step 2) {
                if (data[i] == 0 && data[i + 1] == 0) continue // padding, not a code
                codes += decode(data[i], data[i + 1])
            }
        }
        return codes.toList()
    }

    /** From the monitor-status PID (01 01): is the check-engine lamp on, and how many codes are stored. */
    fun milStatus(firstByte: Int): Pair<Boolean, Int> = ((firstByte and 0x80) != 0) to (firstByte and 0x7F)
}
