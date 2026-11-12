package com.natan.obd.obd

import kotlinx.coroutines.flow.Flow

/** One reading of the car. Fuel fields are only set when freshly measured and supported. */
data class ObdSample(
    val rpm: Int,
    val speedKmh: Int,
    val loadPercent: Double,
    val fuelRateLph: Double? = null,
    val fuelLevelPercent: Double? = null,
    // Engine vitals: null when the car doesn't report them. Shown live, not part of the trip score.
    val coolantC: Int? = null,
    val intakeC: Int? = null,
    val oilC: Int? = null,
    val throttlePercent: Double? = null,
    val manifoldKpa: Int? = null,
    val voltage: Double? = null,
)

/** Where vehicle data comes from: a real adapter or the simulator. */
interface ObdSource {
    /** Cold flow; collecting starts the session, cancelling it ends it. Link failures throw IOException. */
    fun samples(): Flow<ObdSample>
}
