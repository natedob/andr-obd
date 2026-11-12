package com.natan.obd.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natan.obd.data.Driver
import com.natan.obd.data.DriverDao
import com.natan.obd.data.DriverSummary
import com.natan.obd.data.Trip
import com.natan.obd.data.TripDao
import com.natan.obd.data.TripSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val summaries: List<DriverSummary> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val driverNames: Map<Long, String> = emptyMap(),
    val filterDriverId: Long? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    driverDao: DriverDao,
    tripDao: TripDao,
) : ViewModel() {
    private val filter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        driverDao.observeSummaries(), driverDao.observeAll(), tripDao.observeAll(), filter,
    ) { summaries, drivers, trips, filterId ->
        HistoryUiState(
            summaries = summaries,
            trips = if (filterId == null) trips else trips.filter { it.driverId == filterId },
            driverNames = drivers.associate { it.id to it.name },
            filterDriverId = filterId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(driverId: Long?) { filter.value = driverId }
}

data class TripDetailUiState(
    val loaded: Boolean = false,
    val trip: Trip? = null,
    val driverName: String = "",
    val samples: List<TripSample> = emptyList(),
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tripDao: TripDao,
    driverDao: DriverDao,
) : ViewModel() {
    private val tripId: Long = checkNotNull(savedState["id"])

    val uiState: StateFlow<TripDetailUiState> = combine(tripDao.observe(tripId), driverDao.observeAll()) { trip, drivers ->
        TripDetailUiState(
            loaded = true,
            trip = trip,
            driverName = drivers.firstOrNull { it.id == trip?.driverId }?.name.orEmpty(),
            samples = if (trip != null) tripDao.samples(tripId) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        tripDao.deleteById(tripId)
        onDone()
    }
}
