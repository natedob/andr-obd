package com.natan.obd.ui.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natan.obd.data.Driver
import com.natan.obd.data.DriverDao
import com.natan.obd.data.DriverSummary
import com.natan.obd.data.RoutePoint
import com.natan.obd.data.Trip
import com.natan.obd.data.TripDao
import com.natan.obd.data.TripSample
import com.natan.obd.share.ShareCard
import com.natan.obd.share.Sharing
import com.natan.obd.share.TripCsv
import com.natan.obd.ui.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
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
    @ApplicationContext private val context: Context,
    private val driverDao: DriverDao,
    private val tripDao: TripDao,
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

    /** Writes every trip (not just the filtered ones) to a CSV in the share cache; null if there are none. */
    suspend fun exportCsv(): File? = withContext(Dispatchers.IO) {
        val trips = tripDao.observeAll().first()
        if (trips.isEmpty()) return@withContext null
        val names = driverDao.observeAll().first().associate { it.id to it.name }
        File(Sharing.sharedDir(context), "androbd-trips.csv").apply { writeText(TripCsv.build(trips, names)) }
    }
}

data class TripDetailUiState(
    val loaded: Boolean = false,
    val trip: Trip? = null,
    val driverName: String = "",
    val samples: List<TripSample> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
            route = if (trip != null) tripDao.route(tripId) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    /** Renders the shareable score card for this trip; null if the trip is gone. */
    suspend fun renderCard(units: Units): File? = withContext(Dispatchers.IO) {
        val state = uiState.value
        val trip = state.trip ?: return@withContext null
        ShareCard.render(context, trip, state.driverName, units)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        tripDao.deleteById(tripId)
        onDone()
    }
}
