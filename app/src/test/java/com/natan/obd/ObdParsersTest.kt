package com.natan.obd

import com.natan.obd.obd.ObdParsers
import com.natan.obd.obd.Pid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdParsersTest {
    @Test fun decodesRpm() = assertEquals(1726.0, ObdParsers.decode("41 0C 1A F8", Pid.RPM)!!, 0.01)
    @Test fun decodesSpeed() = assertEquals(60.0, ObdParsers.decode("41 0D 3C", Pid.SPEED)!!, 0.01)
    @Test fun decodesLoad() = assertEquals(50.2, ObdParsers.decode("41 04 80", Pid.LOAD)!!, 0.1)
    @Test fun decodesFuelRate() = assertEquals(5.0, ObdParsers.decode("41 5E 00 64", Pid.FUEL_RATE)!!, 0.01)
    @Test fun decodesMaf() = assertEquals(12.0, ObdParsers.decode("41 10 04 B0", Pid.MAF)!!, 0.01)

    @Test fun toleratesSpacelessAndMultilineReplies() {
        assertEquals(1726.0, ObdParsers.decode("410C1AF8", Pid.RPM)!!, 0.01)
        assertEquals(1726.0, ObdParsers.decode("SEARCHING...\r41 0C 1A F8", Pid.RPM)!!, 0.01)
    }

    @Test fun toleratesCanHeaders() =
        assertEquals(1726.0, ObdParsers.decode("7E8 04 41 0C 1A F8", Pid.RPM)!!, 0.01)

    @Test fun rejectsErrorsAndGarbage() {
        assertNull(ObdParsers.decode("NO DATA", Pid.RPM))
        assertNull(ObdParsers.decode("UNABLE TO CONNECT", Pid.RPM))
        assertNull(ObdParsers.decode("?", Pid.RPM))
        assertNull(ObdParsers.decode("", Pid.RPM))
        assertNull(ObdParsers.decode("41 0C 1A F", Pid.RPM)) // truncated
        assertNull(ObdParsers.decode("41 0C ZZ ZZ", Pid.RPM)) // not hex
    }

    @Test fun rejectsRepliesWithTooFewBytes() = assertNull(ObdParsers.decode("41 0C 1A", Pid.RPM))

    @Test fun rejectsReplyForADifferentPid() = assertNull(ObdParsers.decode("41 0D 3C", Pid.RPM))

    @Test fun mafConversionMatchesKnownValue() {
        // 14.7 g/s of air is 1 g/s of fuel = 3600 g/h / 740 g/L = 4.86 L/h
        assertEquals(4.86, ObdParsers.mafToLitresPerHour(14.7), 0.01)
    }
}
