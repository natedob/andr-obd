package com.natan.obd.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/** GPS via the plain framework LocationManager, so no Google Play services dependency. */
@SuppressLint("MissingPermission") // callers check hasPermission()
class LocationTracker @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
    }

    fun locations(): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        // An explicit object: lambdas crash on Android < 11, where the other callbacks aren't default methods.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location) }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Required on older Android versions")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
        awaitClose { lm.removeUpdates(listener) }
    }
}
