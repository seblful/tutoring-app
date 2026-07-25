package com.seblful.repetit.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.calendar.CalendarPermission
import com.seblful.repetit.data.repository.AppSettings
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.SettingsRepository
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
    private val calendarPermission: CalendarPermission,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setNotificationsEnabled(enabled) }
    }

    fun setReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch { settings.setReminderLeadMinutes(minutes) }
    }

    fun setAutoCompleteEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoCompleteEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDefaultLessonMinutes(minutes: Int) {
        viewModelScope.launch { settings.setDefaultLessonMinutes(minutes) }
    }

    fun setCurrency(currency: String) {
        viewModelScope.launch { settings.setCurrency(currency) }
    }

    /**
     * Toggles calendar sync. Enabling backfills events for existing classes; disabling removes the
     * events we created.
     *
     * Enabling is **refused** unless the calendar permission is already held — checked here rather
     * than trusted to the caller, because this flag is what the whole mirror keys off and a screen
     * that forgot the check would leave sync switched on with nothing able to write. A caller that
     * gets [CalendarSyncToggle.PERMISSION_NEEDED] should request the permission (see
     * `AndroidCalendarPermission.REQUIRED`) and call again. Disabling never needs permission —
     * clearing up after ourselves must always be possible.
     */
    fun setCalendarSyncEnabled(enabled: Boolean): CalendarSyncToggle {
        if (enabled && !calendarPermission.isGranted()) return CalendarSyncToggle.PERMISSION_NEEDED
        viewModelScope.launch {
            settings.setCalendarSyncEnabled(enabled)
            if (enabled) classes.resyncCalendar() else classes.clearCalendar()
        }
        return CalendarSyncToggle.APPLIED
    }
}

/** The outcome of asking to turn calendar sync on or off. */
enum class CalendarSyncToggle {
    /** The setting was changed (the sync/clear-up follows asynchronously). */
    APPLIED,

    /** Nothing changed: enabling needs the calendar permission, which isn't granted yet. */
    PERMISSION_NEEDED,
}
