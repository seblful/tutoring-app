package com.seblful.klas.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings screen.
 *
 * Most settings are just stored, so they go through one [update] instead of a wrapper each — the
 * wrappers only ever wrapped a `launch`. The two below have their own methods because changing them
 * does something *besides* storing a value, and that obligation must not be left to the screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val classes: ClassRepository,
    private val calendarPermission: CalendarPermission,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * The permissions the screen must request after a [CalendarSyncToggle.PERMISSION_NEEDED].
     * Surfaced here so the screen needs no import from `calendar/` at all.
     */
    val calendarPermissionsRequired: Array<String> get() = calendarPermission.required

    /** Stores a changed setting, e.g. `update { it.copy(currency = "RUB") }`. */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /**
     * Toggles inviting students to their class events by email, then re-mirrors so the change
     * reaches the lessons already in the calendar — turning it off **withdraws** the invitations
     * already sent, rather than only sparing future lessons.
     *
     * Needs no permission check of its own: the resync no-ops unless calendar sync is on, and
     * enabling *that* is what the permission gates.
     */
    fun setEmailInvitesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.update { it.copy(emailInvitesEnabled = enabled) }
            classes.resyncCalendar()
        }
    }

    /**
     * Toggles calendar sync. Enabling backfills events for existing classes; disabling removes the
     * events we created.
     *
     * Enabling is **refused** unless the calendar permission is already held — checked here rather
     * than trusted to the caller, because this flag is what the whole mirror keys off and a screen
     * that forgot the check would leave sync switched on with nothing able to write. A caller that
     * gets [CalendarSyncToggle.PERMISSION_NEEDED] should request the permission and call again.
     * Disabling never needs permission — clearing up after ourselves must always be possible.
     */
    fun setCalendarSyncEnabled(enabled: Boolean): CalendarSyncToggle {
        if (enabled && !calendarPermission.isGranted()) return CalendarSyncToggle.PERMISSION_NEEDED
        viewModelScope.launch {
            settings.update { it.copy(calendarSyncEnabled = enabled) }
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
