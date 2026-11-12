package com.natan.obd.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.natan.obd.trip.ScoreTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the user can configure. Persisted, so it survives restarts (unlike the old Intent extras). */
data class AppSettings(
    val tankLitres: Double = 50.0,
    val optimalRpm: Int = 2000,
    val optimalSpeed: Int = 80,
    val optimalLoad: Double = 25.0,
    val optimalConsumption: Double = 6.0,
    val demoMode: Boolean = false,
    val lastDeviceAddress: String? = null,
    val selectedDriverId: Long = -1,
    val useImperial: Boolean = false,
    val recordRoute: Boolean = false,
    val speedAlertEnabled: Boolean = false,
    val speedAlertKmh: Int = 130,
    val dieselEngine: Boolean = false,
) {
    fun toTargets() = ScoreTargets(optimalRpm, optimalSpeed, optimalLoad, optimalConsumption)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[TANK] = new.tankLitres
            prefs[RPM] = new.optimalRpm
            prefs[SPEED] = new.optimalSpeed
            prefs[LOAD] = new.optimalLoad
            prefs[CONS] = new.optimalConsumption
            prefs[DEMO] = new.demoMode
            prefs[DRIVER] = new.selectedDriverId
            prefs[IMPERIAL] = new.useImperial
            prefs[ROUTE] = new.recordRoute
            prefs[ALERT_ON] = new.speedAlertEnabled
            prefs[ALERT_KMH] = new.speedAlertKmh
            prefs[DIESEL] = new.dieselEngine
            if (new.lastDeviceAddress == null) prefs.remove(DEVICE) else prefs[DEVICE] = new.lastDeviceAddress
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            tankLitres = this[TANK] ?: d.tankLitres,
            optimalRpm = this[RPM] ?: d.optimalRpm,
            optimalSpeed = this[SPEED] ?: d.optimalSpeed,
            optimalLoad = this[LOAD] ?: d.optimalLoad,
            optimalConsumption = this[CONS] ?: d.optimalConsumption,
            demoMode = this[DEMO] ?: d.demoMode,
            lastDeviceAddress = this[DEVICE],
            selectedDriverId = this[DRIVER] ?: d.selectedDriverId,
            useImperial = this[IMPERIAL] ?: d.useImperial,
            recordRoute = this[ROUTE] ?: d.recordRoute,
            speedAlertEnabled = this[ALERT_ON] ?: d.speedAlertEnabled,
            speedAlertKmh = this[ALERT_KMH] ?: d.speedAlertKmh,
            dieselEngine = this[DIESEL] ?: d.dieselEngine,
        )
    }

    private companion object {
        val TANK = doublePreferencesKey("tank_litres")
        val RPM = intPreferencesKey("optimal_rpm")
        val SPEED = intPreferencesKey("optimal_speed")
        val LOAD = doublePreferencesKey("optimal_load")
        val CONS = doublePreferencesKey("optimal_consumption")
        val DEMO = booleanPreferencesKey("demo_mode")
        val DEVICE = stringPreferencesKey("last_device")
        val DRIVER = longPreferencesKey("selected_driver")
        val IMPERIAL = booleanPreferencesKey("imperial")
        val ROUTE = booleanPreferencesKey("record_route")
        val ALERT_ON = booleanPreferencesKey("speed_alert")
        val ALERT_KMH = intPreferencesKey("speed_alert_kmh")
        val DIESEL = booleanPreferencesKey("diesel_engine")
    }
}
