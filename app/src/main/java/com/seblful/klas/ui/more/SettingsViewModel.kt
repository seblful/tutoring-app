package com.seblful.klas.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.SettingsChange
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.work.Maintenance
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
    private val calendar: CalendarMirror,
    private val reminders: ReminderScheduler,
    private val maintenance: Maintenance,
    private val calendarPermission: CalendarPermission,
    private val notificationPermission: NotificationPermission,
    private val exactAlarms: ExactAlarmPermission,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** he settings row, as the screen draws it. */
    val state: StateFlow<Loadable<AppSettings>> = settings.observe()
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

    private val permissionChecks = MutableStateFlow(0)

    /**
     * What will actually happen to a reminder — **both** platform answers, next to the tutor's intent in
     * [AppSettings.notificationsEnabled]. See [ReminderDelivery] for why there are two.
     */
    val reminderDelivery: StateFlow<ReminderDelivery> =
        combine(settings.observe(), permissionChecks) { s, _ ->
            ReminderDelivery.of(
                enabled = s.notificationsEnabled,
                permitted = notificationPermission.isGranted(),
                exact = exactAlarms.isGranted(),
            )
        }.hold(this, ReminderDelivery.ON_TIME)

    /**
     * What is actually happening to the calendar mirror — the platform's answer, beside the tutor's intent
     * in [AppSettings.calendarSyncEnabled]. See [CalendarDelivery].
     */
    val calendarDelivery: StateFlow<CalendarDelivery> =
        combine(settings.observe(), permissionChecks) { s, _ ->
            CalendarDelivery.of(enabled = s.calendarSyncEnabled, permitted = calendarPermission.isGranted())
        }.hold(this, CalendarDelivery.OFF)

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
        appScope.write(failed, Unit) { apply(transform) }
    }

    private suspend fun apply(transform: (AppSettings) -> AppSettings) {
        val change = settings.update(transform)
        when (change.calendarWork) {
            SettingsChange.CalendarWork.RESYNC -> calendar.resyncAll()
            SettingsChange.CalendarWork.WITHDRAW -> calendar.clear()
            SettingsChange.CalendarWork.NONE -> Unit
        }
        // One call answers both, because both are discharged by an upkeep pass: it re-derives the
        // reminder plan and re-plans the digests from the row that has just been written.
        if (change.replansReminders || change.replansDigests) reminders.refreshNow()
        // Run here and now, rather than by asking the scheduler to get round to it: the tutor is
        // standing in front of the switch they just flipped, and the pass has to be able to report
        // back. It re-derives the reminder plan too, so it subsumes the refresh above.
        //
        // `run` and not `runUnwatched`: this pass **is** watched, and reporting it twice — once in the
        // snackbar the tutor is looking at and once in the notification tray — would be the app
        // telling them something they had just been told.
        if (change.sweepsDueLessons) {
            val settled = maintenance.run()
            if (settled.lessons > 0) swept.send(settled.lessons)
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
            val backlog = maintenance.dueForCompletion()
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

/** The outcome of asking to turn calendar sync on or off. */
enum class CalendarSyncToggle {
    /** The setting was changed (the sync/clear-up follows asynchronously). */
    APPLIED,

    /** Nothing changed: enabling needs the calendar permission, which isn't granted yet. */
    PERMISSION_NEEDED,
}

/**
 * What will actually happen to a lesson reminder — the one answer the reminder row reports.
 *
 * A reminder needs **two** grants and they fail differently, which is why this is a named answer and not
 * a boolean. `POST_NOTIFICATIONS` decides whether it appears at all; `SCHEDULE_EXACT_ALARM` decides
 * whether it appears *when promised*. The second one is the one the app was silent about: it is absent by
 * default on Android 14+, `ReminderAlarms` falls back to `setAndAllowWhileIdle` without it, and Doze may
 * then hold the alarm until its next maintenance window. So «занятие через час» could arrive during the
 * lesson, on a feature whose whole worth is the hour of warning, with the switch reading «включено» and
 * the row underneath saying everything was fine.
 *
 * [LATE] is deliberately not a refusal. The inexact alarm is a real fallback — late is much better than
 * never, and the app declines to demand `USE_EXACT_ALARM`, which Play reserves for alarm-clock and
 * calendar apps. What the tutor is owed is the sentence, not a dialog.
 *
 * Ordered by what the tutor can do about it: [OFF] is their own choice, [BLOCKED] and [LATE] are the
 * platform's, and a [BLOCKED] reminder has nothing to be late about — so the checks stack rather than
 * combine.
 */
enum class ReminderDelivery {

    /** The tutor has switched reminders off. Nothing to report about the platform. */
    OFF,

    /** On, permitted, exact — the reminder arrives when it says it will. */
    ON_TIME,

    /** On, but nothing can be posted: the permission is missing or notifications are off app-wide. */
    BLOCKED,

    /** On and permitted, but only an inexact alarm may be set, so the reminder may arrive late. */
    LATE,
    ;

    companion object {
        /**
         * Pure, and therefore tested: the interesting part is the *precedence* of three booleans, and on
         * a device two of them cannot be moved without walking through system settings.
         */
        fun of(enabled: Boolean, permitted: Boolean, exact: Boolean): ReminderDelivery = when {
            !enabled -> OFF
            !permitted -> BLOCKED
            !exact -> LATE
            else -> ON_TIME
        }
    }
}

/**
 * What is actually happening to the calendar mirror — the one answer the sync row reports, as
 * [ReminderDelivery] is for the reminder row.
 *
 * Its permission is revoked from system settings while the app is away, which leaves the switch **on** and
 * the mirror dead. Off is the tutor's choice and has nothing to report about the platform.
 */
enum class CalendarDelivery {

    /** The tutor has switched sync off. */
    OFF,

    /** On, and the calendar may be written to. */
    ON,

    /** On, but the permission is gone, so nothing is mirrored. */
    BLOCKED,
    ;

    companion object {
        fun of(enabled: Boolean, permitted: Boolean): CalendarDelivery = when {
            !enabled -> OFF
            !permitted -> BLOCKED
            else -> ON
        }
    }
}

/** hat turning the reminder setting on owes the platform. */
sealed interface NotificationsIntent {

    /** Nothing to ask: it was switched off, or the permission is already granted. */
    data object Nothing : NotificationsIntent

    /**
     * Show the platform's prompt — `rememberNotificationPermissionRequest`. What the prompt asks for is the
     * platform's business, so it is not carried here.
     */
    data object Request : NotificationsIntent

    /**
     * There is nothing the app may ask for: notifications are off for the whole app in system settings,
     * which only the tutor can undo. Say so instead of showing a request that would be dismissed
     * instantly.
     */
    data object BlockedBySystem : NotificationsIntent
}
