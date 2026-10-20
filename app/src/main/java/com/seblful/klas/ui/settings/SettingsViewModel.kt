package com.seblful.klas.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.write
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** he settings screen. */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val workflow: SettingsWorkflow,
    private val calendarPermission: CalendarPermission,
    private val notificationPermission: NotificationPermission,
    private val exactAlarms: ExactAlarmPermission,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val permissionChecks = MutableStateFlow(0)

    /** Preference values and permission status arrive together, without invented loading defaults. */
    val state: StateFlow<Loadable<SettingsState>> = combine(settings.observe(), permissionChecks) { s, _ ->
        val permitted = notificationPermission.isGranted()
        SettingsState(
            settings = s,
            notifications = NotificationDelivery.of(s, permitted),
            reminders = ReminderDelivery.of(s.notificationsEnabled, permitted, exactAlarms.isGranted()),
            calendar = CalendarDelivery.of(s.calendarSyncEnabled, calendarPermission.isGranted()),
        )
    }
        .ready()
        .hold(this, Loadable.Loading)

    /** How many lessons a sweep just settled. Reported once, as a message — never replayed. */
    val swept = UiEvents<Int>()

    /** A change that could not be stored or carried out — a database or calendar error, reported once. */
    val failed = UiEvents<Unit>()

    private val _pendingSweep = MutableStateFlow<Int?>(null)

    /**
     * The size of a backlog the tutor has been asked about and has not yet answered for — null while
     * nothing is being asked. See [setAutoCompleteEnabled] for why the question exists.
     */
    val pendingSweep: StateFlow<Int?> = _pendingSweep.asStateFlow()

    /** urns reminders [on] — **storing the setting and saying what that owes the platform, in one call.** */
    fun setNotificationsEnabled(on: Boolean): NotificationsIntent {
        update { it.copy(notificationsEnabled = on) }
        // Turning it off asks nothing; turning it on owes exactly what the blocked row owes, so the two
        // share one definition rather than spelling the same three arms twice.
        return if (!on) NotificationsIntent.Nothing else unblockNotifications()
    }

    /**
     * Switches a digest on or off with [transform] — and, switched on, says what the platform must be
     * asked for it to arrive, exactly as [setNotificationsEnabled] does for reminders. A digest is a
     * notification too: with reminders off on Android 13+, nothing had ever asked for the permission, so
     * a digest switched on was never shown and no row said why.
     */
    fun setDigestEnabled(on: Boolean, transform: (AppSettings) -> AppSettings): NotificationsIntent {
        update(transform)
        return if (!on) NotificationsIntent.Nothing else unblockNotifications()
    }

    /**
     * How the platform can be asked to let a reminder through — **what the [ReminderDelivery.BLOCKED] row
     * does when tapped**, and what [setNotificationsEnabled] returns once it has stored an `on`.
     */
    fun unblockNotifications(): NotificationsIntent = when {
        notificationPermission.isGranted() -> NotificationsIntent.Nothing
        notificationPermission.canAsk -> NotificationsIntent.Request
        // Nothing left to ask: notifications are off for the whole app in system settings, or the
        // permission is denied for good. Both are undoable only by the tutor and only from that page,
        // so a dialog here would close itself and read as the app doing nothing.
        else -> NotificationsIntent.BlockedBySystem
    }

    /** Re-asks the platform. Called on resume and after a permission dialog closes. */
    fun refreshPermissions() {
        permissionChecks.value += 1
    }

    /**
     * The calendar prompt closed: sync goes on through [setCalendarSyncEnabled] — the one place its refusal
     * lives — if the permission is now held, and stays off otherwise.
     */
    fun calendarPermissionAnswered(): CalendarSyncToggle {
        refreshPermissions()
        return setCalendarSyncEnabled(true)
    }

    /**
     * Stores a changed setting, e.g. `update { it.copy(currency = Currency.RUB) }` — and discharges
     * whatever the change owes the world outside the row.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        appScope.write(failed, Unit) {
            val lessons = workflow.update(transform)
            if (lessons > 0) swept.send(lessons)
        }
    }

    /**
     * Turns auto-completion on or off — **asking first** whenever switching it on would complete a past
     * lesson, however few.
     */
    fun setAutoCompleteEnabled(on: Boolean) {
        if (!on) {
            _pendingSweep.value = null
            update { it.copy(autoCompleteEnabled = false) }
            return
        }
        viewModelScope.launch {
            val backlog = workflow.dueForCompletion()
            if (backlog > 0) _pendingSweep.value = backlog
            else update { it.copy(autoCompleteEnabled = true) }
        }
    }

    /** Answers [pendingSweep] with yes: the setting goes on, and [update] settles the backlog. */
    fun confirmPendingSweep() {
        _pendingSweep.value = null
        update { it.copy(autoCompleteEnabled = true) }
    }

    /** Answers [pendingSweep] with no. Nothing is stored, so the switch stays off. */
    fun dismissPendingSweep() {
        _pendingSweep.value = null
    }

    /**
     * Toggles calendar sync. The backfill on enabling and the withdrawal on disabling are [update]'s
     * to discharge, like every other consequence of a settings change; what this method owns is the
     * **refusal**.
     */
    fun setCalendarSyncEnabled(enabled: Boolean): CalendarSyncToggle {
        if (enabled && !calendarPermission.isGranted()) return CalendarSyncToggle.PERMISSION_NEEDED
        update { it.copy(calendarSyncEnabled = enabled) }
        return CalendarSyncToggle.APPLIED
    }
}
