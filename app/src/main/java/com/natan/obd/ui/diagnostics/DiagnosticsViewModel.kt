package com.natan.obd.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natan.obd.bluetooth.BluetoothController
import com.natan.obd.bluetooth.LinkState
import com.natan.obd.data.SettingsRepository
import com.natan.obd.diag.DiagnosticsController
import com.natan.obd.diag.DtcReport
import com.natan.obd.trip.TripController
import com.natan.obd.trip.TripState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DiagPhase {
    data object Idle : DiagPhase
    data object Busy : DiagPhase
    data class Done(val report: DtcReport, val justCleared: Boolean) : DiagPhase
    data class Failed(val message: String) : DiagPhase
}

sealed interface SupportPhase {
    data object Idle : SupportPhase
    data object Busy : SupportPhase
    data class Done(val pids: Set<Int>, val diesel: Boolean) : SupportPhase
    data class Failed(val message: String) : SupportPhase
}

data class DiagUiState(
    val phase: DiagPhase = DiagPhase.Idle,
    val support: SupportPhase = SupportPhase.Idle,
    val canScan: Boolean = false,
    val tripRunning: Boolean = false,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: DiagnosticsController,
    settingsRepository: SettingsRepository,
    bluetooth: BluetoothController,
    tripController: TripController,
) : ViewModel() {
    private val phase = MutableStateFlow<DiagPhase>(DiagPhase.Idle)
    private val support = MutableStateFlow<SupportPhase>(SupportPhase.Idle)
    private val settings = settingsRepository

    val uiState: StateFlow<DiagUiState> = combine(
        phase, support, settingsRepository.settings, bluetooth.state, tripController.state,
    ) { phase, support, settings, link, trip ->
        DiagUiState(
            phase = phase,
            support = support,
            canScan = settings.demoMode || link is LinkState.Connected,
            tripRunning = trip is TripState.Running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagUiState())

    fun scan() = runScan(justCleared = false)

    fun checkSupport() {
        if (support.value == SupportPhase.Busy) return
        support.value = SupportPhase.Busy
        viewModelScope.launch {
            val diesel = settings.settings.first().dieselEngine
            diagnostics.supportedPids()
                .onSuccess { support.value = SupportPhase.Done(it, diesel) }
                .onFailure { support.value = SupportPhase.Failed(it.message.orEmpty()) }
        }
    }

    fun clear() {
        if (phase.value == DiagPhase.Busy) return
        phase.value = DiagPhase.Busy
        viewModelScope.launch {
            diagnostics.clear()
                .onSuccess { runScan(justCleared = true) }
                .onFailure { phase.value = DiagPhase.Failed(it.message.orEmpty()) }
        }
    }

    private fun runScan(justCleared: Boolean) {
        if (phase.value == DiagPhase.Busy && !justCleared) return
        phase.value = DiagPhase.Busy
        viewModelScope.launch {
            diagnostics.scan()
                .onSuccess { phase.value = DiagPhase.Done(it, justCleared) }
                .onFailure { phase.value = DiagPhase.Failed(it.message.orEmpty()) }
        }
    }
}
