package com.natan.obd.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.natan.obd.MainActivity
import com.natan.obd.R
import com.natan.obd.data.SettingsRepository
import com.natan.obd.ui.Units
import com.natan.obd.ui.formatDuration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A real foreground service: it shows the ongoing-trip notification and keeps the process alive
 * while the screen is off. All trip logic lives in [TripController].
 */
@AndroidEntryPoint
class TripService : Service() {

    @Inject lateinit var controller: TripController
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    @Volatile private var units = Units(imperial = false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            if (controller.state.value is TripState.Idle) stopSelf()
            return START_NOT_STICKY
        }

        val driverId = intent?.getLongExtra(EXTRA_DRIVER_ID, -1L) ?: -1L
        val driverName = intent?.getStringExtra(EXTRA_DRIVER_NAME).orEmpty()

        // Must happen right away after startForegroundService(), before any real work.
        // The location type may only be claimed when the permission is actually held (Android 14+ enforces it).
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            (if (locationGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(driverName, LiveData()), type)
        if (driverId < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        controller.start(driverId, driverName) // idempotent: a second Start is ignored
        if (!observing) {
            observing = true
            val manager = getSystemService(NotificationManager::class.java)
            scope.launch {
                units = Units(settingsRepository.settings.first().useImperial)
                controller.state.collect { state ->
                    when (state) {
                        is TripState.Running -> manager.notify(NOTIFICATION_ID, buildNotification(state.driverName, state.live))
                        TripState.Idle -> {
                            ServiceCompat.stopForeground(this@TripService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }
        // Not sticky: a killed trip can't be resumed meaningfully, and restarting it blindly would be wrong.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.stop() // saves whatever was recorded if the system tears us down
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(driverName: String, live: LiveData): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (live.hasData) {
            getString(R.string.notif_text, formatDuration(live.elapsedSec), units.distance(live.distanceKm), units.speed(live.speedKmh))
        } else getString(R.string.notif_connecting)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setContentTitle(getString(R.string.notif_title, driverName))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.natan.obd.STOP_TRIP"
        const val EXTRA_DRIVER_ID = "driver_id"
        const val EXTRA_DRIVER_NAME = "driver_name"

        fun startIntent(context: Context, driverId: Long, driverName: String): Intent =
            Intent(context, TripService::class.java)
                .putExtra(EXTRA_DRIVER_ID, driverId)
                .putExtra(EXTRA_DRIVER_NAME, driverName)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
