package com.natan.obd.obd

import kotlinx.coroutines.flow.Flow

/** One reading of the car. Fuel fields are only set when freshly measured and supported. */
data class ObdSample(
    val rpm: Int,
    val speedKmh: Int,
    val loadPercent: Double,
    val fuelRateLph: Double? = null,
    val fuelLevelPercent: Double? = null,
)

/** Where vehicle data comes from: a real adapter or the simulator. */
interface ObdSource {
    /** Cold flow; collecting starts the session, cancelling it ends it. Link failures throw IOException. */
    fun samples(): Flow<ObdSample>
}
