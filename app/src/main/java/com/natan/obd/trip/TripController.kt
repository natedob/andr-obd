package com.natan.obd.trip

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import com.natan.obd.R
import com.natan.obd.bluetooth.BluetoothController
import com.natan.obd.data.AppSettings
import com.natan.obd.data.SettingsRepository
import com.natan.obd.data.RoutePoint
import com.natan.obd.data.Trip
import com.natan.obd.data.TripDao
import com.natan.obd.data.TripSample
import com.natan.obd.obd.ElmObdSource
import com.natan.obd.obd.ObdSample
import com.natan.obd.obd.ObdSource
import com.natan.obd.obd.SimulatedObdSource
import com.natan.obd.route.LocationTracker
import com.natan.obd.route.RouteRecorder
import com.natan.obd.route.SimulatedRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What the dashboard shows while a trip runs. */
data class LiveData(
    val hasData: Boolean = false,
    val rpm: Int = 0,
    val speedKmh: Int = 0,
    val loadPercent: Double = 0.0,
    val elapsedSec: Int = 0,
    val distanceKm: Double = 0.0,
    val avgSpeedKmh: Int = 0,
    val vitals: Vitals = Vitals(),
    val coaching: Coaching = Coaching(),
    /** True while above the user's speed-alert limit (only ever set when the alert is enabled). */
    val speeding: Boolean = false,
    // Fuel: each is null when the car doesn't give us enough to work it out.
    val tankPercent: Double? = null,
    val instantLph: Double? = null,
    val instantL100: Double? = null,
    val avgL100: Double? = null,
) {
    val hasFuelInfo: Boolean get() = tankPercent != null || instantLph != null || avgL100 != null
}

/** Engine vitals shown on the dashboard; a null field means the car doesn't report it. */
data class Vitals(
    val coolantC: Int? = null,
    val intakeC: Int? = null,
    val oilC: Int? = null,
    val throttlePercent: Double? = null,
    val manifoldKpa: Int? = null,
    val voltage: Double? = null,
) {
    val any: Boolean get() = listOf(coolantC, intakeC, oilC, throttlePercent, manifoldKpa, voltage).any { it != null }
}

sealed interface TripState {
    data object Idle : TripState
    data class Running(val driverName: String, val live: LiveData) : TripState
}

sealed interface TripEvent {
    data class Saved(val tripId: Long) : TripEvent
    data class Message(val text: String) : TripEvent
}

/**
 * The single owner of a trip's lifecycle. It lives in its own process-wide scope, so a trip survives
 * screen rotation and navigation; [TripService] just keeps the process alive and shows the
 * notification. Everything is one sequential collection of samples (no mutexes, no sleeps).
 */
@Singleton
class TripController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetooth: BluetoothController,
    private val settingsRepository: SettingsRepository,
    private val tripDao: TripDao,
    private val locationTracker: LocationTracker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var stopSignal: CompletableDeferred<Unit>? = null

    private val _state = MutableStateFlow<TripState>(TripState.Idle)
    val state: StateFlow<TripState> = _state

    private val _events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TripEvent> = _events

    @Synchronized
    fun start(driverId: Long, driverName: String) {
        if (job?.isActive == true) return // starting twice must not spawn a second set of loops
        val signal = CompletableDeferred<Unit>()
        stopSignal = signal
        _state.value = TripState.Running(driverName, LiveData())
        job = scope.launch { runTrip(driverId, driverName, signal) }
    }

    /** Ends the trip and saves it. Safe to call at any time, including when idle. */
    fun stop() {
        stopSignal?.complete(Unit)
    }

    private suspend fun runTrip(driverId: Long, driverName: String, stop: CompletableDeferred<Unit>) {
        val settings = settingsRepository.settings.first()
        val source = createSource(settings)
        if (source == null) {
            _events.emit(TripEvent.Message(context.getString(R.string.err_no_adapter)))
            _state.value = TripState.Idle
            return
        }

        val accumulator = TripAccumulator(settings.tankLitres)
        val coach = CoachingAdvisor(settings.toTargets())
        val alert = if (settings.speedAlertEnabled) SpeedAlertPolicy(settings.speedAlertKmh) else null
        val recorder = RouteRecorder()
        val smoother = ConsumptionSmoother()
        val eco = AtomicInteger(0)
        // Demo mode walks a fake route at the simulated speed; a real trip uses the phone's GPS.
        val fakeRoute = if (settings.demoMode && settings.recordRoute) {
            val start = locationTracker.lastKnown()
            SimulatedRoute(start?.latitude ?: SimulatedRoute.FALLBACK_LAT, start?.longitude ?: SimulatedRoute.FALLBACK_LON)
        } else null
        val startedAt = System.currentTimeMillis()
        val t0 = SystemClock.elapsedRealtime()
        var failure: String? = null
        var linkLost = false

        // GPS is a bonus: if it fails or isn't allowed, the trip carries on without a route.
        val gps = if (!settings.demoMode && settings.recordRoute && locationTracker.hasPermission()) {
            scope.launch {
                try {
                    locationTracker.locations().collect { loc ->
                        recorder.add(loc.latitude, loc.longitude, SystemClock.elapsedRealtime() - t0, eco.get())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // ignore: no route, but the trip itself is unaffected
                }
            }
        } else null

        // Sampling runs in a child job so a stop request can cancel it and we still get to save.
        val collector = scope.launch {
            try {
                var previousMs = 0L
                source.samples().collect { sample ->
                    val now = SystemClock.elapsedRealtime() - t0
                    accumulator.add(sample, now)
                    val coaching = coach.update(sample.rpm, sample.speedKmh, sample.loadPercent, now)
                    eco.set(coaching.ecoNow)
                    if (alert?.update(sample.speedKmh, now) == true) beep()
                    fakeRoute?.let {
                        it.advance(sample.speedKmh, now - previousMs)
                        recorder.add(it.lat, it.lon, now, coaching.ecoNow)
                    }
                    previousMs = now
                    publish(driverName, sample, accumulator, now, coaching, alert?.isOver == true, smoother.update(sample.fuelRateLph, sample.speedKmh, now))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failure = e.message ?: context.getString(R.string.err_link_lost)
                linkLost = true
            } catch (e: Exception) {
                failure = e.message ?: e.javaClass.simpleName
            }
        }
        val stopWatcher = scope.launch { stop.await(); collector.cancel() }
        collector.join()
        stopWatcher.cancel()
        gps?.cancel()

        withContext(NonCancellable) {
            if (linkLost) bluetooth.markLost()
            save(driverId, startedAt, accumulator, recorder, settings, failure)
            _state.value = TripState.Idle
        }
    }

    private fun createSource(settings: AppSettings): ObdSource? =
        if (settings.demoMode) SimulatedObdSource() else bluetooth.openClient()?.let { ElmObdSource(it, diesel = settings.dieselEngine) }

    /** A short double-beep on the notification stream; failures (some devices lack the tone) are harmless. */
    private fun beep() {
        scope.launch {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
                delay(700)
                tone.release()
            }
        }
    }

    private fun publish(
        driverName: String,
        sample: ObdSample,
        acc: TripAccumulator,
        nowMs: Long,
        coaching: Coaching,
        speeding: Boolean,
        consumption: Consumption,
    ) {
        val stats = acc.snapshot()
        // The trip average needs a little distance behind it, or it swings wildly in the first minutes.
        val avgL100 = stats.fuelLitres
            ?.takeIf { stats.distanceKm >= MIN_DISTANCE_FOR_AVERAGE_KM && it > 0 }
            ?.let { it / stats.distanceKm * 100 }
        _state.value = TripState.Running(
            driverName,
            LiveData(
                hasData = true,
                rpm = sample.rpm,
                speedKmh = sample.speedKmh,
                loadPercent = sample.loadPercent,
                elapsedSec = (nowMs / 1000).toInt(),
                distanceKm = stats.distanceKm,
                avgSpeedKmh = stats.avgSpeed,
                vitals = Vitals(
                    sample.coolantC, sample.intakeC, sample.oilC,
                    sample.throttlePercent, sample.manifoldKpa, sample.voltage,
                ),
                coaching = coaching,
                speeding = speeding,
                tankPercent = sample.tankPercent,
                instantLph = consumption.litresPerHour,
                instantL100 = consumption.litresPer100,
                avgL100 = avgL100,
            ),
        )
    }

    private suspend fun save(
        driverId: Long,
        startedAt: Long,
        acc: TripAccumulator,
        recorder: RouteRecorder,
        settings: AppSettings,
        failure: String?,
    ) {
        val stats = acc.snapshot()
        if (stats.durationMs < MIN_TRIP_MS) {
            val text = failure ?: context.getString(R.string.msg_trip_too_short)
            _events.emit(TripEvent.Message(text))
            return
        }

        // Consumption from a few metres of driving is meaningless (and divides by ~0), so require a minimum.
        val consumption = stats.fuelLitres
            ?.takeIf { stats.distanceKm >= MIN_DISTANCE_FOR_CONSUMPTION_KM && it > 0 }
            ?.let { it / stats.distanceKm * 100 }
        val score = ScoreCalculator.calculate(stats.avgRpm, stats.avgSpeed, stats.avgLoad, consumption, settings.toTargets())

        val trip = Trip(
            driverId = driverId,
            startedAt = startedAt,
            durationSec = (stats.durationMs / 1000).toInt(),
            distanceKm = stats.distanceKm,
            avgRpm = stats.avgRpm,
            avgSpeed = stats.avgSpeed,
            maxSpeed = stats.maxSpeed,
            avgLoad = stats.avgLoad,
            fuelLitres = stats.fuelLitres,
            consumptionL100 = consumption,
            score = score.total,
            speedScore = score.speed,
            rpmScore = score.rpm,
            loadScore = score.load,
            efficiencyScore = score.efficiency,
        )
        val samples = acc.points.map { TripSample(tripId = 0, tSec = it.tSec, rpm = it.rpm, speed = it.speed, load = it.load) }
        val route = recorder.fixes.map { RoutePoint(tripId = 0, tSec = it.tSec, lat = it.lat, lon = it.lon, eco = it.eco) }
        try {
            val id = tripDao.insertWithSamples(trip, samples, route)
            if (failure != null) _events.emit(TripEvent.Message(context.getString(R.string.msg_trip_partial)))
            _events.emit(TripEvent.Saved(id))
        } catch (e: Exception) {
            // The driver was deleted mid-trip, or the disk is full: say so instead of crashing the service.
            _events.emit(TripEvent.Message(context.getString(R.string.msg_trip_save_failed)))
        }
    }

    private companion object {
        const val MIN_TRIP_MS = 10_000L
        const val MIN_DISTANCE_FOR_CONSUMPTION_KM = 0.5
        const val MIN_DISTANCE_FOR_AVERAGE_KM = 0.3
    }
}
