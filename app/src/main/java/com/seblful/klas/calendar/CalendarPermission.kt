package com.seblful.klas.calendar

/**
 * Whether the app currently holds the calendar permissions the mirror needs.
 *
 * A port so that "calendar sync may only be switched on once the permission is granted" can be a
 * rule a view model enforces rather than a branch in a composable — a screen can forget the branch,
 * and the flag it flips is what the whole mirror keys off. Asking the device is the only part that
 * needs Android, so that is all this exposes.
 */
interface CalendarPermission {

    /**
     * True when both read and write calendar access have been granted.
     *
     * The only question the port answers. **Asking** for the grant is not on it: only a screen can host the
     * platform's prompt, so that is [rememberCalendarPermissionRequest]'s. The port used to hand out the
     * Android permission names for the screen to launch, which put a list of `Manifest.permission`s into a
     * view model — a shape no other platform has.
     */
    fun isGranted(): Boolean
}
