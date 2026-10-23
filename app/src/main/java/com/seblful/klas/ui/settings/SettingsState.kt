package com.seblful.klas.ui.settings

import com.seblful.klas.data.settings.AppSettings

data class SettingsState(
    val settings: AppSettings,
    val notifications: NotificationDelivery,
    val reminders: ReminderDelivery,
    val calendar: CalendarDelivery,
    val facts: IndexFacts = IndexFacts(),
)

/**
 * What the index's lower card says live of the app's data and the app itself, where it said what each page
 * is for: how many subjects, the version, whether a crash is on record.
 */
data class IndexFacts(val subjects: Int = 0, val version: String = "", val crashRecorded: Boolean = false)

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

/** What turning the reminder setting on owes the platform. */
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

enum class NotificationDelivery {
    OFF,
    ENABLED,
    BLOCKED,
    ;

    companion object {
        fun of(settings: AppSettings, permitted: Boolean): NotificationDelivery = when {
            settings.enabledNotificationCount == 0 -> OFF
            !permitted -> BLOCKED
            else -> ENABLED
        }
    }
}

internal val AppSettings.enabledNotificationCount: Int
    get() = listOf(
        notificationsEnabled, dailyAgendaEnabled, unfinishedDigestEnabled, debtDigestEnabled, autoCompleteEnabled,
    ).count { it }

/** Calendar email reminders use the same lead even when phone reminders are off. */
internal val AppSettings.canEditReminderLead: Boolean
    get() = notificationsEnabled || calendarSyncEnabled
