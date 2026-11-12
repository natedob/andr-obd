package com.natan.obd

import com.natan.obd.obd.DtcDescriptions
import com.natan.obd.obd.DtcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcParserTest {
    @Test fun decodesAllFourSystems() {
        assertEquals("P0133", DtcParser.decode(0x01, 0x33))
        assertEquals("P0420", DtcParser.decode(0x04, 0x20))
        assertEquals("C0123", DtcParser.decode(0x41, 0x23))
        assertEquals("B0100", DtcParser.decode(0x81, 0x00))
        assertEquals("U0100", DtcParser.decode(0xC1, 0x00))
        assertEquals("P2002", DtcParser.decode(0x20, 0x02))
    }

    @Test fun legacyReplyWithPaddingHasNoCountByte() =
        assertEquals(listOf("P0133", "P0420"), DtcParser.parse("43 01 33 04 20 00 00"))

    @Test fun canReplyStartsWithCountByte() =
        assertEquals(listOf("P0133", "P0420"), DtcParser.parse("43 02 01 33 04 20"))

    @Test fun canReplyWithNoCodes() {
        assertEquals(emptyList<String>(), DtcParser.parse("43 00"))
        assertEquals(emptyList<String>(), DtcParser.parse("43 00 00 00 00 00 00"))
    }

    @Test fun severalEcusAreMergedWithoutDuplicates() =
        assertEquals(listOf("P0133", "P0420"), DtcParser.parse("43 01 33 00 00 00 00\r43 01 33 04 20 00 00"))

    @Test fun multiFrameCanReply() {
        // 3 codes = 1 mode byte + 1 count + 6 data bytes = 8 bytes, split over numbered frames
        val reply = "008\r0: 43 03 01 33 04\r1: 20 01 71"
        assertEquals(listOf("P0133", "P0420", "P0171"), DtcParser.parse(reply))
    }

    @Test fun pendingCodesUseMode47() {
        assertEquals(listOf("P0300"), DtcParser.parse("47 01 03 00", "47"))
        assertEquals(emptyList<String>(), DtcParser.parse("47 01 03 00", "43")) // wrong mode is ignored
    }

    @Test fun noDataAndGarbageGiveNoCodes() {
        assertEquals(emptyList<String>(), DtcParser.parse("NO DATA"))
        assertEquals(emptyList<String>(), DtcParser.parse("SEARCHING...\rUNABLE TO CONNECT"))
        assertEquals(emptyList<String>(), DtcParser.parse(""))
        assertEquals(emptyList<String>(), DtcParser.parse("43 01 3")) // truncated
    }

    @Test fun milStatusBits() {
        assertEquals(true to 3, DtcParser.milStatus(0x83))
        assertEquals(false to 0, DtcParser.milStatus(0x00))
        assertEquals(false to 2, DtcParser.milStatus(0x02))
    }

    @Test fun descriptionsExistForCommonCodesAndAreNullForUnknown() {
        assertNotNull(DtcDescriptions.describe("P0420"))
        assertNotNull(DtcDescriptions.describe("p0300")) // case-insensitive
        assertNull(DtcDescriptions.describe("P1234"))
        assertTrue(DtcDescriptions.describe("P0171")!!.contains("lean"))
    }
}
