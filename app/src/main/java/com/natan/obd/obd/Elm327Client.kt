package com.natan.obd.obd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal ELM327 AT/OBD client. Reads are polled (available() + delay), so they stay cancellable
 * and time out instead of blocking a thread forever on a dead Bluetooth link.
 */
class Elm327Client(
    private val input: InputStream,
    private val output: OutputStream,
    private val defaultTimeoutMs: Long = 3_000,
) {
    /** Sends [command] and returns everything the adapter said before its '>' prompt. */
    suspend fun send(command: String, timeoutMs: Long = defaultTimeoutMs): String = withContext(Dispatchers.IO) {
        while (input.available() > 0) input.skip(input.available().toLong()) // drop stale bytes
        output.write("$command\r".toByteArray())
        output.flush()

        val buffer = ByteArray(256)
        val reply = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            ensureActive()
            val available = input.available()
            if (available > 0) {
                val n = input.read(buffer, 0, minOf(available, buffer.size))
                if (n < 0) throw IOException("Adapter closed the connection")
                reply.append(String(buffer, 0, n))
                if (reply.contains('>')) break
            } else {
                if (System.currentTimeMillis() > deadline) throw IOException("Adapter did not answer \"$command\"")
                delay(10)
            }
        }
        reply.toString().substringBefore('>').trim()
    }

    /** Resets the adapter, configures it for compact output and makes sure the car answers. */
    suspend fun initialise() {
        send("ATZ", 4_000)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) send(cmd)
        // The first real request triggers protocol auto-detection, which can take several seconds.
        val reply = send("0100", 20_000).filterNot { it.isWhitespace() }.uppercase()
        if ("4100" !in reply) throw IOException("The adapter can't reach the car. Is the ignition on?")
    }

    /**
     * Asks which PIDs the car supports. They come in blocks of 32: 01 00 covers 01-20, 01 20 covers 21-40, and
     * so on, and the last PID of each block says whether the next block exists.
     */
    suspend fun readSupportedPids(): Set<Int> {
        val all = HashSet<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val bytes = ObdParsers.bytes(send("01%02X".format(base)), "%02X".format(base)) ?: break
            val block = SupportedPids.parseMask(bytes, base)
            all += block
            if ((base + 0x20) !in block) break
            base += 0x20
        }
        return all
    }

    /** Reads one PID; null means the car doesn't support it or had no data right now. */
    suspend fun read(pid: Pid): Double? = ObdParsers.decode(send(pid.request), pid)
}
