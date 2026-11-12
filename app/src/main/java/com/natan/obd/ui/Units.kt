package com.natan.obd.ui

import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt

/** Display-only unit conversion. Everything is stored and calculated in metric. */
class Units(val imperial: Boolean) {
    val speedUnit: String get() = if (imperial) "mph" else "km/h"
    val distanceUnit: String get() = if (imperial) "mi" else "km"
    val tempUnit: String get() = if (imperial) "°F" else "°C"

    fun speedValue(kmh: Double): Double = if (imperial) kmh * KM_TO_MI else kmh
    fun distanceValue(km: Double): Double = if (imperial) km * KM_TO_MI else km

    fun speed(kmh: Int): String = "${speedValue(kmh.toDouble()).roundToInt()} $speedUnit"
    fun distance(km: Double): String = "${formatDecimal(distanceValue(km))} $distanceUnit"
    fun temperature(celsius: Int): String = "${if (imperial) (celsius * 9.0 / 5 + 32).roundToInt() else celsius} $tempUnit"

    /** L/100 km, or US miles per gallon. */
    fun consumption(litresPer100: Double): String =
        if (imperial && litresPer100 > 0) "${formatDecimal(MPG_FACTOR / litresPer100)} mpg"
        else "${formatDecimal(litresPer100)} L/100 km"

    fun volume(litres: Double): String =
        if (imperial) "${formatDecimal(litres * LITRES_TO_GALLONS, 2)} gal" else "${formatDecimal(litres, 2)} L"

    private companion object {
        const val KM_TO_MI = 0.621371
        const val MPG_FACTOR = 235.215
        const val LITRES_TO_GALLONS = 0.264172
    }
}

val LocalUnits = compositionLocalOf { Units(imperial = false) }
