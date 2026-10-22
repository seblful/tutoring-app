package com.seblful.klas.lock

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Whether the app is open behind its lock — see `Privacy.appLock`. Closed until the tutor unlocks, and
 * closed again once the app has been in the background for the relock delay. Measured on a monotonic
 * clock, so moving the phone's time never opens it.
 *
 * It only says whether the content may show; whether the lock is on at all is the setting's, and whether
 * the phone can unlock anything is [available]'s — a phone with no screen lock is never locked out.
 */
@SingleIn(AppScope::class)
class AppLock(private val time: TimeSource.WithComparableMarks) {

    @Inject constructor() : this(TimeSource.Monotonic)

    private val _open = MutableStateFlow(false)

    /** Whether the content may show while the lock is on. */
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _available = MutableStateFlow(true)

    /** Whether the phone has biometrics or a screen lock to unlock with — asked each time the app comes up. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var away: ComparableTimeMark? = null
    private var prompting = false

    /** The app left the screen — unless for the unlock prompt itself, which can open a screen of its own. */
    fun backgrounded() {
        if (!prompting) away = time.markNow()
    }

    /**
     * The app is back. It locks if it was away for [relockAfterMinutes] or longer, and when the delay is
     * not known yet (null), since closed is the safe way to be wrong.
     */
    fun foregrounded(relockAfterMinutes: Int?, available: Boolean) {
        _available.value = available
        val since = away ?: return
        away = null
        if (relockAfterMinutes == null || since.elapsedNow() >= relockAfterMinutes.minutes) _open.value = false
    }

    fun prompting(active: Boolean) {
        prompting = active
    }

    fun unlocked() {
        prompting = false
        _open.value = true
    }
}
