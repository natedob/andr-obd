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

    @Test fun decodesVitals() {
        assertEquals(83.0, ObdParsers.decode("41 05 7B", Pid.COOLANT_TEMP)!!, 0.01) // 123 - 40
        assertEquals(-10.0, ObdParsers.decode("41 0F 1E", Pid.INTAKE_TEMP)!!, 0.01) // below freezing
        assertEquals(60.0, ObdParsers.decode("41 5C 64", Pid.OIL_TEMP)!!, 0.01)
        assertEquals(50.2, ObdParsers.decode("41 11 80", Pid.THROTTLE)!!, 0.1)
        assertEquals(101.0, ObdParsers.decode("41 0B 65", Pid.MAP)!!, 0.01)
        assertEquals(14.8, ObdParsers.decode("41 42 39 D0", Pid.VOLTAGE)!!, 0.001)
    }

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

    @Test fun decodesLambda() {
        assertEquals(1.0, ObdParsers.decode("41 24 80 00 7F FF", Pid.LAMBDA_24)!!, 0.001)
        assertEquals(2.0, ObdParsers.decode("41 34 FF FF 80 00", Pid.LAMBDA_34)!!, 0.001)
    }

    @Test fun dieselFuelUsesLambdaAndDieselDensity() {
        // 20 g/s of air at lambda 2: AFR 29, so 0.69 g/s of fuel = 2.98 L/h at 832 g/L.
        assertEquals(2.98, ObdParsers.dieselLitresPerHour(20.0, 2.0), 0.01)
        // The petrol formula on the same reading would claim ~2x as much, which is why the engine type matters.
        assertEquals(6.6, ObdParsers.mafToLitresPerHour(20.0), 0.05)
    }

    @Test fun mafConversionMatchesKnownValue() {
        // 14.7 g/s of air is 1 g/s of fuel = 3600 g/h / 740 g/L = 4.86 L/h
        assertEquals(4.86, ObdParsers.mafToLitresPerHour(14.7), 0.01)
    }
}
