package com.seblful.klas.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.SettingsChange
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.hold
import com.seblful.klas.work.Maintenance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings screen.
 *
 * Every setting goes through one [update], and what a change owes besides being stored — the
 * calendar re-earned, the reminder plan re-derived, the finished lessons settled — is discharged
 * there, from the classification the store hands back ([SettingsChange]). Two settings used to have
 * named methods carrying their tails by hand, and the third that owed one rode the generic path in
 * silence: changing the reminder lead left every already-mirrored event at the old lead until that
 * lesson happened to be edited. The two methods left with a name exist for what they **refuse or
 * ask** — [setCalendarSyncEnabled] for the permission gate, [setAutoCompleteEnabled] for the
 * question — not for their tails.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val calendar: CalendarMirror,
    private val reminders: ReminderScheduler,
    private val maintenance: Maintenance,
    private val calendarPermission: CalendarPermission,
    private val notificationPermission: NotificationPermission,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.observe()
        .hold(this, AppSettings())

    /** How many lessons a sweep just settled. Reported once, as a message — never replayed. */
    val swept = UiEvents<Int>()

    private val _pendingSweep = MutableStateFlow<Int?>(null)

    /**
     * The size of a backlog the tutor has been asked about and has not yet answered for — null while
     * nothing is being asked. See [setAutoCompleteEnabled] for why the question exists.
     *
     * State rather than a one-shot event, because it must survive a rotation: a question the screen
     * forgot halfway would leave the switch off with the tutor believing they had turned it on.
     */
    val pendingSweep: StateFlow<Int?> = _pendingSweep.asStateFlow()

    /** Bumped by [refreshPermissions] to re-ask the platform; a permission is not observable. */
    private val permissionChecks = MutableStateFlow(0)

    /**
     * Whether a reminder would actually be shown — the platform's answer, next to the tutor's intent in
     * [AppSettings.notificationsEnabled].
     *
     * Both are needed because they can disagree, and only one of them was ever on screen. The setting is
     * on by default; the permission is not granted by default; `AndroidNotifier` no-ops without it. So a
     * fresh install showed «Напоминания» switched on while no reminder could ever appear, and nothing
     * anywhere said so — the app's one time-critical feature, silently dead, reading as enabled.
     *
     * Not a `Flow` from the platform, because there is no such thing: a permission changes while the app
     * is away, in system settings. The screen re-asks on resume through [refreshPermissions].
     */
    val notificationsPermitted: StateFlow<Boolean> = permissionChecks
        .map { notificationPermission.isGranted() }
        .hold(this, true)

    /** What to request when [notificationsPermitted] is false; empty where there is nothing to ask. */
    val notificationPermissionsRequired: Array<String> get() = notificationPermission.required

    /**
     * What has to happen, besides storing the setting, when the tutor turns reminders [on].
     *
     * Three outcomes, and the screen used to work them out in a nest of three `if`s inside the toggle's
     * `onCheckedChange`: turning it off asks nothing, turning it on with the permission already granted
     * asks nothing, turning it on without it asks the platform — unless the platform has nothing to ask,
     * which means notifications are off for the whole app in system settings and only the tutor can undo
     * it. That last arm is the one worth naming: it looks like the permission case and behaves like a
     * dead end, and getting it wrong shows a dialog that can never appear.
     */
    fun notificationsIntent(on: Boolean): NotificationsIntent = when {
        !on || notificationsPermitted.value -> NotificationsIntent.Nothing
        notificationPermissionsRequired.isNotEmpty() ->
            NotificationsIntent.Request(notificationPermissionsRequired)
        else -> NotificationsIntent.BlockedBySystem
    }

    /** Re-asks the platform. Called on resume and after a permission dialog closes. */
    fun refreshPermissions() {
        permissionChecks.value += 1
    }

    /**
     * The permissions the screen must request after a [CalendarSyncToggle.PERMISSION_NEEDED].
     * Surfaced here so the screen needs no import from `calendar/` at all.
     */
    val calendarPermissionsRequired: Array<String> get() = calendarPermission.required

    /**
     * Stores a changed setting, e.g. `update { it.copy(currency = Currency.RUB) }` — and discharges
     * whatever the change owes the world outside the row. Which changes owe what is decided by
     * [SettingsChange], beside the fields themselves, so a setting cannot lose its tail by lacking
     * a named method here: turning invites off withdraws the invitations already sent, moving the
     * reminder lead re-mirrors the events carrying the old one and re-derives the alarm plan.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val change = settings.update(transform)
            when (change.calendarWork) {
                SettingsChange.CalendarWork.RESYNC -> calendar.resyncAll()
                SettingsChange.CalendarWork.WITHDRAW -> calendar.clear()
                SettingsChange.CalendarWork.NONE -> Unit
            }
            if (change.replansReminders) reminders.refreshNow()
            // Run here and now, rather than by asking the scheduler to get round to it: the tutor is
            // standing in front of the switch they just flipped, and the pass has to be able to report
            // back. It re-derives the reminder plan too, so it subsumes the refresh above.
            if (change.sweepsDueLessons) {
                val settled = maintenance.run()
                if (settled > 0) swept.send(settled)
            }
        }
    }

    /**
     * Turns auto-completion on or off — **asking first** whenever switching it on would complete a past
     * lesson, however few.
     *
     * Turning it on completes every lesson that has already finished, however long ago, and completing
     * a lesson bills it. That is what the setting says it does and generally what a tutor turning it on
     * wants, but it is still recognized income moving off one tap, so the number is put to them first.
     *
     * **However few, and not above some size.** There was a threshold here, on the argument that a
     * couple of stale lessons are not worth a dialog. It is the wrong shape for this: the tutor cannot
     * see the backlog before they flip the switch, so "small enough not to mention" is a judgement made
     * on their behalf about *their* money, and the number that gets billed unasked is exactly the number
     * they had no way to check. Zero is the only backlog worth not asking about, and that case asks
     * nothing because there is nothing to say.
     *
     * **Declining leaves the setting off**, which is the only honest answer available: the sweep has no
     * lower bound, so with the setting on that backlog would be settled by the next upkeep pass
     * regardless — a "no" that only postponed it would be a lie. Wanting auto-completion *without* the
     * backlog is therefore not expressible here, and deliberately so; the tutor cancels the old lessons
     * they do not want billed, then turns it on.
     *
     * The **background** pass asks nobody and sweeps whatever has finished, as it always has. Nothing
     * can be asked from a worker, and once the switch is on that is the arrangement the tutor agreed to.
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
     * **refusal**. Enabling is refused unless the calendar permission is already held — checked here
     * rather than trusted to the caller, because this flag is what the whole mirror keys off and a
     * screen that forgot the check would leave sync switched on with nothing able to write. A caller
     * that gets [CalendarSyncToggle.PERMISSION_NEEDED] should request the permission and call again.
     * Disabling never needs permission — clearing up after ourselves must always be possible.
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
 * What turning the reminder setting on owes the platform.
 *
 * Storing the setting always happens; this is the rest of it. Kept as a value rather than as branches in
 * the toggle's callback so the dead end — notifications switched off for the whole app, where asking
 * would show a dialog that can never appear — is a named outcome instead of an `else`.
 */
sealed interface NotificationsIntent {

    /** Nothing to ask: it was switched off, or the permission is already granted. */
    data object Nothing : NotificationsIntent

    /** Ask the platform for these. */
    data class Request(val permissions: Array<String>) : NotificationsIntent {
        // Array equality is identity, which would make this data class lie about being equal.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Request && permissions.contentEquals(other.permissions))

        override fun hashCode(): Int = permissions.contentHashCode()
    }

    /**
     * There is nothing the app may ask for: notifications are off for the whole app in system settings,
     * which only the tutor can undo. Say so instead of showing a request that would be dismissed
     * instantly.
     */
    data object BlockedBySystem : NotificationsIntent
}
