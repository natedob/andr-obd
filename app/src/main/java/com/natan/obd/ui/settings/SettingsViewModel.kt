package com.natan.obd.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natan.obd.data.AppSettings
import com.natan.obd.data.SettingsRepository
import com.natan.obd.data.TripDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val tripDao: TripDao,
) : ViewModel() {
    /** Null until DataStore has loaded, so the form never flashes default values over real ones. */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repository.update(transform) }

    fun clearHistory() = viewModelScope.launch { tripDao.deleteAll() }
}
