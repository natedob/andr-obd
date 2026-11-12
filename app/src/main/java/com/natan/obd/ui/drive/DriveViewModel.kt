package com.natan.obd.ui.drive

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natan.obd.R
import com.natan.obd.bluetooth.BluetoothController
import com.natan.obd.bluetooth.LinkState
import com.natan.obd.bluetooth.PairedDevice
import com.natan.obd.data.AppSettings
import com.natan.obd.data.Driver
import com.natan.obd.data.DriverDao
import com.natan.obd.data.SettingsRepository
import com.natan.obd.trip.TripController
import com.natan.obd.trip.TripEvent
import com.natan.obd.trip.TripService
import com.natan.obd.trip.TripState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val drivers: List<Driver> = emptyList(),
    val selectedDriver: Driver? = null,
    val settings: AppSettings = AppSettings(),
    val link: LinkState = LinkState.Disconnected,
    val trip: TripState = TripState.Idle,
    val bluetoothSupported: Boolean = true,
)

sealed interface DriveEvent {
    data class Message(val text: String) : DriveEvent
    data class TripSaved(val tripId: Long) : DriveEvent
}

@HiltViewModel
class DriveViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val driverDao: DriverDao,
    private val settingsRepository: SettingsRepository,
    private val bluetooth: BluetoothController,
    private val tripController: TripController,
) : ViewModel() {

    val uiState: StateFlow<DriveUiState> = combine(
        driverDao.observeAll(), settingsRepository.settings, bluetooth.state, tripController.state,
    ) { drivers, settings, link, trip ->
        DriveUiState(
            drivers = drivers,
            selectedDriver = drivers.firstOrNull { it.id == settings.selectedDriverId } ?: drivers.firstOrNull(),
            settings = settings,
            link = link,
            trip = trip,
            bluetoothSupported = bluetooth.isSupported,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveUiState())

    private val _events = Channel<DriveEvent>(Channel.BUFFERED)
    val events: Flow<DriveEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            tripController.events.collect { event ->
                _events.send(
                    when (event) {
                        is TripEvent.Saved -> DriveEvent.TripSaved(event.tripId)
                        is TripEvent.Message -> DriveEvent.Message(event.text)
                    },
                )
            }
        }
    }

    fun selectDriver(id: Long) = viewModelScope.launch {
        settingsRepository.update { it.copy(selectedDriverId = id) }
    }

    fun addDriver(name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isEmpty()) return@launch
        try {
            val id = driverDao.insert(Driver(name = clean))
            settingsRepository.update { it.copy(selectedDriverId = id) }
        } catch (e: SQLiteConstraintException) {
            _events.send(DriveEvent.Message(appContext.getString(R.string.msg_driver_exists, clean)))
        }
    }

    fun deleteDriver(driver: Driver) = viewModelScope.launch { driverDao.delete(driver) }

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(demoMode = enabled) }
    }

    fun pairedDevices(): List<PairedDevice> = bluetooth.pairedDevices()

    fun connect(address: String) = viewModelScope.launch {
        bluetooth.connect(address)
            .onSuccess { settingsRepository.update { it.copy(lastDeviceAddress = address) } }
            .onFailure { _events.send(DriveEvent.Message(it.message ?: appContext.getString(R.string.err_connect_failed))) }
    }

    fun disconnect() = bluetooth.disconnect()

    fun startTrip() {
        val driver = uiState.value.selectedDriver ?: return
        ContextCompat.startForegroundService(appContext, TripService.startIntent(appContext, driver.id, driver.name))
    }

    fun stopTrip() = tripController.stop()
}
