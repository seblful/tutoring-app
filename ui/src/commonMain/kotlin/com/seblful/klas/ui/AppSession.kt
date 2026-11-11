package com.seblful.klas.ui

import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.lock.AppLock
import com.seblful.klas.notifications.Shortcuts
import com.seblful.klas.pro.ProAccess
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.text.Texts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the settings ask of the platform outside the screens: [AppSession] decides when, the host how. */
interface HostEffects {

    /** Whether the system's picture of the app — recent apps, the app switcher — must hide its content. */
    fun hideFromSwitcher(hide: Boolean)

    /** Publishes [Shortcuts.launcher] on the app's icon, labelled in [texts]' language. */
    fun publishShortcuts(texts: Texts)
}

/**
 * The app coming and going, as both hosts report it, and what the settings make of that: the app lock, the
 * switcher's picture and the icon's shortcuts. One owner, so a setting cannot be honoured on one platform
 * and forgotten on the other; the hosts only forward their lifecycle and implement [HostEffects]. It also asks the
 * store what Klas Pro the tutor owns, once each time the app opens, which is when a lapsed subscription is noticed.
 */
@SingleIn(AppScope::class)
@Inject
class AppSession(
    private val settings: SettingsRepository,
    private val displayLocale: DisplayLocale,
    private val appLock: AppLock,
    private val pro: ProAccess,
) {
    /**
     * Keeps [host] in step with the settings for as long as the caller collects, applying the current values
     * first. Collect it on the main thread: both platforms' effects touch the window or the launcher.
     */
    suspend fun keepApplied(host: HostEffects): Unit = coroutineScope {
        launch { displayLocale.observe().collect { host.publishShortcuts(Texts.of(it)) } }
        launch { runCatchingCancellable { pro.refresh() } }
        launch {
            settings.observe().map { it.privacy.hidesContent }.distinctUntilChanged().collect(host::hideFromSwitcher)
        }
    }

    /** The app is on screen again; [canUnlock] is whether the phone can unlock anything now. */
    fun foregrounded(canUnlock: Boolean) = appLock.foregrounded(settings.privacy.value?.relockAfterMinutes, canUnlock)

    /** The app left the screen. Not for an Android configuration change, which only recreates the window. */
    fun backgrounded() = appLock.backgrounded()
}

/** A locked app hides itself from the switcher too, or the switcher would show what the lock hides. */
internal val Privacy.hidesContent: Boolean get() = hideInRecents || appLock
