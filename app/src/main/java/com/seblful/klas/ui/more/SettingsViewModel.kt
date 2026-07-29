package com.seblful.klas.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.hold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    private val calendar: CalendarMirror,
    private val calendarPermission: CalendarPermission,
    private val notificationPermission: NotificationPermission,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.observe()
        .hold(this, AppSettings())

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
            calendar.resyncAll()
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
            if (enabled) calendar.resyncAll() else calendar.clear()
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
