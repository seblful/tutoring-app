package com.seblful.klas.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.SettingsChange
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.hold
import com.seblful.klas.ui.common.ready
import com.seblful.klas.work.Maintenance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val exactAlarms: ExactAlarmPermission,
) : ViewModel() {

    /**
     * The settings row, as the screen draws it.
     *
     * [Loadable] rather than `AppSettings()`, because **every row on this screen is a stored value and
     * the class's defaults are not a neutral placeholder — they are a different tutor's settings.** A
     * default `AppSettings` says reminders are on, sync is off, the currency is BYN and the lesson is an
     * hour, so for the frame or two before the row was read, a tutor who had turned reminders off watched
     * the switch flip itself off, and one on RUB watched «Валюта» change under them. A settings screen
     * that lies for a frame is worse than one that waits for a frame: the flip looks like the app doing
     * something. See [Loadable].
     */
    val state: StateFlow<Loadable<AppSettings>> = settings.observe()
        .ready()
        .hold(this, Loadable.Loading)

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
     * What will actually happen to a reminder — **both** platform answers, next to the tutor's intent in
     * [AppSettings.notificationsEnabled]. See [ReminderDelivery] for why there are two.
     *
     * All three facts are needed because they can disagree, and for a long time only the first was on
     * screen. The setting is on by default; neither permission is granted by default. So a fresh install
     * showed «Напоминания» switched on while no reminder could appear at all — and once that was fixed, an
     * Android 14 install showed it working while every reminder was arriving whenever Doze got round to
     * it.
     *
     * Not a `Flow` from the platform, because there is no such thing: a permission changes while the app
     * is away, in system settings. The screen re-asks on resume through [refreshPermissions].
     */
    val reminderDelivery: StateFlow<ReminderDelivery> =
        combine(settings.observe(), permissionChecks) { s, _ ->
            ReminderDelivery.of(
                enabled = s.notificationsEnabled,
                permitted = notificationPermission.isGranted(),
                exact = exactAlarms.isGranted(),
            )
        }.hold(this, ReminderDelivery.ON_TIME)

    /** What to request when [notificationsPermitted] is false; empty where there is nothing to ask. */
    val notificationPermissionsRequired: List<String> get() = notificationPermission.required

    /**
     * Whether the calendar may actually be written to — the platform's answer, beside the tutor's intent
     * in [AppSettings.calendarSyncEnabled].
     *
     * The notification row already did this and this one did not, though the permission behind it is
     * revocable from exactly the same place: system settings, while the app is away. Revoking it leaves
     * the switch **on** and the mirror silently dead — `CalendarContractSync` no-ops without the grant —
     * which is the same defect the reminder row was fixed for, on the feature whose whole promise is that
     * the lessons are also somewhere else.
     *
     * Note what this is *not*: the reason sync cannot be **enabled** without the grant. That rule is
     * [setCalendarSyncEnabled]'s and is a refusal; this is a report. Off is off either way, so it only has
     * something to say while the setting is on.
     *
     * Re-asked on resume through [refreshPermissions], like its counterpart: a permission is not a `Flow`.
     */
    val calendarPermitted: StateFlow<Boolean> = permissionChecks
        .map { calendarPermission.isGranted() }
        .hold(this, true)

    /**
     * Turns reminders [on] — **storing the setting and saying what that owes the platform, in one call.**
     *
     * Three outcomes, and the screen used to work them out in a nest of three `if`s inside the toggle's
     * `onCheckedChange`: turning it off asks nothing, turning it on with the permission already granted
     * asks nothing, turning it on without it asks the platform — unless the platform has nothing to ask,
     * which means notifications are off for the whole app in system settings and only the tutor can undo
     * it. That last arm is the one worth naming: it looks like the permission case and behaves like a
     * dead end, and getting it wrong shows a dialog that can never appear. All three past the first are
     * [unblockNotifications], because the blocked row owes the tutor the same answer this does.
     *
     * **The store and the ask used to be two calls the screen had to make, in order, over the same
     * argument** — `update { it.copy(notificationsEnabled = on) }` and then `notificationsIntent(on)` —
     * with nothing binding them. Either half could be omitted: asking without storing requests a permission
     * for a setting that was never saved, and storing without asking leaves reminders reading «включено»
     * with no permission and no dialog, which is precisely the defect [reminderDelivery] was added to
     * report. An ordering constraint that lives only in a composable is also one no test can reach, and
     * `SettingsViewModelTest` could exercise either half and never the pair. Its sibling
     * [setCalendarSyncEnabled] had the right shape all along: one call, one outcome.
     */
    fun setNotificationsEnabled(on: Boolean): NotificationsIntent {
        update { it.copy(notificationsEnabled = on) }
        // Turning it off asks nothing; turning it on owes exactly what the blocked row owes, so the two
        // share one definition rather than spelling the same three arms twice.
        return if (!on) NotificationsIntent.Nothing else unblockNotifications()
    }

    /**
     * How the platform can be asked to let a reminder through — **what the [ReminderDelivery.BLOCKED] row
     * does when tapped**, and what [setNotificationsEnabled] returns once it has stored an `on`.
     *
     * This exists because the setting is **on by default and the permission is not**, which left no path
     * through the app that ever requested `POST_NOTIFICATIONS`. The only caller was the switch, and a switch
     * already reading «включено» cannot be turned on — so a fresh install had reminders enabled, every
     * notification dropped by `AndroidNotifier`'s permission check, and a row that *described* the dead end
     * without offering a way out. [reminderDelivery] was added to report that state; nothing was added to
     * leave it. This is that, and it is the same shape [ReminderDelivery.LATE] already had: the report and
     * the way out, together, or the report is just a better-worded silence.
     *
     * **Stores nothing**, unlike its sibling. The tutor is unblocking the setting they already have rather
     * than changing it, so there is nothing here to write — which is also why this is safe to call from a
     * row that is only drawn while the setting is on.
     *
     * Asked of the port directly rather than read off [reminderDelivery], for the reason
     * [setCalendarSyncEnabled] asks directly too: this is a decision made at the moment of the tap, and a
     * `WhileSubscribed` flow's `.value` is only as fresh as its last collection.
     */
    fun unblockNotifications(): NotificationsIntent = when {
        notificationPermission.isGranted() -> NotificationsIntent.Nothing
        notificationPermissionsRequired.isNotEmpty() ->
            NotificationsIntent.Request(notificationPermissionsRequired)
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
     * The permissions the screen must request after a [CalendarSyncToggle.PERMISSION_NEEDED].
     * Surfaced here so the screen needs no import from `calendar/` at all.
     */
    val calendarPermissionsRequired: List<String> get() = calendarPermission.required

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
 * What turning the reminder setting on owes the platform.
 *
 * Storing the setting always happens; this is the rest of it. Kept as a value rather than as branches in
 * the toggle's callback so the dead end — notifications switched off for the whole app, where asking
 * would show a dialog that can never appear — is a named outcome instead of an `else`.
 */
sealed interface NotificationsIntent {

    /** Nothing to ask: it was switched off, or the permission is already granted. */
    data object Nothing : NotificationsIntent

    /**
     * Ask the platform for these.
     *
     * A `List` and not an `Array`. The ports below hand out arrays because that is what
     * `ActivityResultContracts.RequestMultiplePermissions` takes, and carrying one up here meant this
     * `data class` had to hand-write `equals`/`hashCode` over `contentEquals` — the standard symptom of an
     * array inside a value type, since array equality is identity and the generated one would have lied.
     * The conversion is now one `toTypedArray()` at the single launcher call site.
     */
    data class Request(val permissions: List<String>) : NotificationsIntent

    /**
     * There is nothing the app may ask for: notifications are off for the whole app in system settings,
     * which only the tutor can undo. Say so instead of showing a request that would be dismissed
     * instantly.
     */
    data object BlockedBySystem : NotificationsIntent
}
