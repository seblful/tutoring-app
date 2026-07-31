package com.seblful.klas.notifications

/**
 * Whether the app may actually post a notification, and what to ask for when it may not.
 *
 * A port for the same reason [com.seblful.klas.calendar.CalendarPermission] is one: whether the
 * platform has granted something is the only part that needs Android, and the *rule* built on top of
 * it belongs in a view model a JVM test can drive.
 *
 * The rule here is deliberately **not** the calendar's. Calendar sync cannot be switched on without
 * its permission, because that flag is what the whole mirror keys off. `notificationsEnabled` keys
 * nothing — it is the tutor saying whether they *want* reminders, and it is on by default. Refusing to
 * let them say yes would be the app arguing with them. What went wrong was subtler: the switch read on,
 * the permission was never granted, `AndroidNotifier` no-opped in silence, and nothing anywhere said
 * that no reminder could ever appear. So intent and platform truth are kept as two separate facts and
 * the settings screen shows both.
 */
interface NotificationPermission {

    /** True when a posted notification would actually be shown. Always true below Android 13. */
    fun isGranted(): Boolean

    /**
     * What to request when [isGranted] is false — empty below Android 13, where there is nothing to
     * ask for. On the port rather than the adapter so the settings screen needs no import from here.
     */
    val required: List<String>
}
