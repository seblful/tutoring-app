package com.seblful.klas.data.settings

/**
 * What the phone shows of a tutor's students and money when it is locked or the app is in the
 * background. **Private unless chosen**: names and amounts stay off the lock screen, and the app lock and
 * hiding from recent apps are there for a tutor who wants them.
 *
 * Device-local like the rest of [AppSettings], so a backup restored onto another phone never turns a lock
 * on there.
 */
data class Privacy(
    /** Whether notifications show lesson names and amounts on a secure lock screen. */
    val lockScreenDetails: Boolean = false,
    /** Whether the Today widget lists lessons when it sits on the lock screen rather than a home screen. */
    val lockScreenWidgets: Boolean = false,
    /** Whether opening the app asks for the phone's biometrics or screen lock. */
    val appLock: Boolean = false,
    /** How long the app may stay in the background before [appLock] asks again; 0 is at once. */
    val relockAfterMinutes: Int = DEFAULT_RELOCK_MINUTES,
    /** Whether the app's preview is blank in recent apps — and screenshots of it are blocked below Android 13. */
    val hideInRecents: Boolean = false,
) {
    companion object {
        const val DEFAULT_RELOCK_MINUTES = 1

        /** The delays Settings offers, in minutes. */
        val RELOCK_CHOICES = listOf(0, 1, 5, 30)
    }
}
