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

    /** True when notifications are enabled for the app and any required runtime permission is granted. */
    fun isGranted(): Boolean

    /**
     * Whether the platform has a prompt to show when [isGranted] is false — false below Android 13, where
     * there is no runtime permission and only the app-wide switch in system settings can be off.
     *
     * A yes or no rather than what to ask for: showing the prompt needs a screen, so that is
     * [rememberNotificationPermissionRequest]'s. The port used to hand out the Android permission names
     * for the screen to launch, which put `Manifest.permission`s into two view models.
     */
    val canAsk: Boolean
}
