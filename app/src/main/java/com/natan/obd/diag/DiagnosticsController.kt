package com.natan.obd.diag

import android.content.Context
import com.natan.obd.R
import com.natan.obd.bluetooth.BluetoothController
import com.natan.obd.data.SettingsRepository
import com.natan.obd.obd.DtcDescriptions
import com.natan.obd.obd.DtcParser
import com.natan.obd.obd.ObdParsers
import com.natan.obd.obd.Pid
import com.natan.obd.trip.TripController
import com.natan.obd.trip.TripState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class Dtc(val code: String, val description: String?)

data class DtcReport(val milOn: Boolean, val stored: List<Dtc>, val pending: List<Dtc>)

/** Reads and clears trouble codes. Never runs during a trip: the adapter is a single serial line. */
@Singleton
class DiagnosticsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetooth: BluetoothController,
    private val settingsRepository: SettingsRepository,
    private val tripController: TripController,
) {
    // Demo mode gets a believable fault that "clears", so the whole flow can be tried without a car.
    private var demoCodes = listOf("P0420", "P0171")

    suspend fun scan(): Result<DtcReport> = guarded {
        if (settingsRepository.settings.first().demoMode) {
            delay(1_200)
            return@guarded DtcReport(demoCodes.isNotEmpty(), demoCodes.map(::dtc), emptyList())
        }
        val client = bluetooth.openClient() ?: throw IOException(context.getString(R.string.err_no_adapter))
        client.initialise()
        val status = ObdParsers.bytes(client.send("0101"), Pid.MONITOR_STATUS)?.firstOrNull()
        val stored = DtcParser.parse(client.send("03", 10_000), "43")
        val pending = DtcParser.parse(client.send("07", 10_000), "47")
        DtcReport(
            milOn = status?.let { DtcParser.milStatus(it).first } ?: stored.isNotEmpty(),
            stored = stored.map(::dtc),
            pending = pending.map(::dtc),
        )
    }

    /** Clears codes and turns the check-engine lamp off. The codes return if the fault is still there. */
    suspend fun clear(): Result<Unit> = guarded {
        if (settingsRepository.settings.first().demoMode) {
            delay(800)
            demoCodes = emptyList()
            return@guarded
        }
        val client = bluetooth.openClient() ?: throw IOException(context.getString(R.string.err_no_adapter))
        client.initialise()
        val reply = client.send("04", 10_000).filterNot { it.isWhitespace() }
        if (!reply.contains("44")) throw IOException(context.getString(R.string.err_clear_failed))
    }

    private fun dtc(code: String) = Dtc(code, DtcDescriptions.describe(code))

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        if (tripController.state.value is TripState.Running) {
            return@withContext Result.failure(IOException(context.getString(R.string.err_trip_running)))
        }
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: context.getString(R.string.err_connect_failed)))
        }
    }
}
