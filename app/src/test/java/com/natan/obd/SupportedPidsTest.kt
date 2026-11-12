package com.natan.obd

import com.natan.obd.obd.Elm327Client
import com.natan.obd.obd.FuelMethod
import com.natan.obd.obd.SupportedPids
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class SupportedPidsTest {
    @Test fun decodesTheClassicExampleMask() {
        // BE 1F A8 13 is the textbook reply to 01 00.
        val pids = SupportedPids.parseMask(intArrayOf(0xBE, 0x1F, 0xA8, 0x13), 0x00)
        assertEquals(setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20), pids)
    }

    @Test fun laterBlocksAreOffsetByTheirBase() {
        assertEquals(setOf(0x21), SupportedPids.parseMask(intArrayOf(0x80, 0, 0, 0), 0x20))
        assertEquals(setOf(0x5E), SupportedPids.parseMask(intArrayOf(0, 0, 0, 0).also { it[0] = 0 }, 0x40).plus(0x5E)) // sanity
        assertEquals(setOf(0x60), SupportedPids.parseMask(intArrayOf(0, 0, 0, 1), 0x40))
    }

    @Test fun shortOrEmptyMasksGiveNothing() = assertTrue(SupportedPids.parseMask(intArrayOf(0xBE, 0x1F), 0).isEmpty())

    @Test fun rangeMarkersAreRecognised() {
        assertTrue(SupportedPids.isRangeMarker(0x20))
        assertTrue(SupportedPids.isRangeMarker(0x60))
        assertFalse(SupportedPids.isRangeMarker(0x0C))
        assertFalse(SupportedPids.isRangeMarker(0x00))
    }

    @Test fun knownPidsHaveNamesAndUnknownOnesDont() {
        assertEquals("Engine fuel rate", SupportedPids.name(0x5E))
        assertEquals("Engine oil temperature", SupportedPids.name(0x5C))
        assertNull(SupportedPids.name(0xF0))
    }

    @Test fun fuelMethodMatchesWhatTheAppDoes() {
        assertEquals(FuelMethod.DIRECT_RATE, SupportedPids.fuelMethod(setOf(0x5E, 0x10), diesel = false))
        assertEquals(FuelMethod.MAF_ESTIMATE, SupportedPids.fuelMethod(setOf(0x10, 0x2F), diesel = false))
        assertEquals(FuelMethod.MAF_LAMBDA_ESTIMATE, SupportedPids.fuelMethod(setOf(0x10, 0x24), diesel = true))
        assertEquals(FuelMethod.MAF_LAMBDA_ESTIMATE, SupportedPids.fuelMethod(setOf(0x10, 0x34), diesel = true))
        // Diesel with air flow but no lambda: refuses to guess and uses the tank gauge.
        assertEquals(FuelMethod.TANK_GAUGE_ONLY, SupportedPids.fuelMethod(setOf(0x10, 0x2F), diesel = true))
        assertEquals(FuelMethod.TANK_GAUGE_ONLY, SupportedPids.fuelMethod(setOf(0x2F), diesel = false))
        assertEquals(FuelMethod.NONE, SupportedPids.fuelMethod(setOf(0x0C), diesel = false))
        assertEquals(FuelMethod.NONE, SupportedPids.fuelMethod(setOf(0x10), diesel = true))
    }

    /** A tiny scripted adapter: answers each command (terminated by CR) with a canned reply and the '>' prompt. */
    private class FakeAdapter(private val replies: Map<String, String>) {
        private val inbox = StringBuilder()
        private val command = StringBuilder()
        val asked = mutableListOf<String>()

        val input = object : InputStream() {
            override fun available(): Int = synchronized(inbox) { inbox.length }
            override fun read(): Int = synchronized(inbox) { if (inbox.isEmpty()) -1 else inbox[0].code.also { inbox.deleteCharAt(0) } }
            override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(inbox) {
                if (inbox.isEmpty()) return -1
                val n = minOf(len, inbox.length)
                for (i in 0 until n) b[off + i] = inbox[i].code.toByte()
                inbox.delete(0, n)
                n
            }
        }
        val output = object : OutputStream() {
            override fun write(b: Int) {
                if (b.toChar() == '\r') {
                    val cmd = command.toString().also { command.clear() }
                    asked += cmd
                    synchronized(inbox) { inbox.append(replies[cmd] ?: "NO DATA").append("\r\r>") }
                } else command.append(b.toChar())
            }
        }
    }

    @Test fun clientWalksTheRangesUntilTheChainEnds() = runBlocking {
        val adapter = FakeAdapter(
            mapOf(
                "0100" to "41 00 BE 1F A8 13", // ...bit 0x20 set: ask for the next block
                "0120" to "41 20 80 00 00 01", // 0x21 and 0x40 supported
                "0140" to "41 40 00 00 00 00", // 0x60 NOT supported: stop here
            ),
        )
        val pids = Elm327Client(adapter.input, adapter.output).readSupportedPids()
        assertEquals(listOf("0100", "0120", "0140"), adapter.asked)
        assertTrue(0x0C in pids && 0x21 in pids && 0x40 in pids)
        assertFalse(0x61 in pids)
    }

    @Test fun clientStopsWhenTheCarDoesNotAnswer() = runBlocking {
        val adapter = FakeAdapter(emptyMap())
        assertTrue(Elm327Client(adapter.input, adapter.output).readSupportedPids().isEmpty())
        assertEquals(listOf("0100"), adapter.asked)
    }
}
