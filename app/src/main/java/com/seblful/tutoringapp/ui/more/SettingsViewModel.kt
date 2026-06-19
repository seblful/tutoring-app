package com.seblful.tutoringapp.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.AppSettings
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val classes: ClassRepository,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setNotificationsEnabled(enabled) }
    }

    /**
     * Toggles calendar sync. Enabling backfills events for existing classes; disabling removes the
     * events we created. The caller is responsible for ensuring calendar permission before enabling.
     */
    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setCalendarSyncEnabled(enabled)
            if (enabled) classes.resyncCalendar() else classes.clearCalendar()
        }
    }
}
