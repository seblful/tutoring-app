package com.seblful.klas.ui

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.DeviceLocales
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.AppLocale
import com.seblful.klas.format.localeFor
import com.seblful.klas.lock.AppLock
import com.seblful.klas.maintenance.Maintenance
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.onboarding.FirstLesson
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Which of the three things the app is showing at the very top.
 *
 * `AppRoot` used to decide this in a `when` over a nullable settings row: still loading, not onboarded,
 * or the tabs. Naming it makes "settings are null" mean *loading* rather than merely being a null check
 * that the two arms below it happen to be ordered around.
 */
enum class RootRoute {

    /** Settings have not arrived yet. A spinner, under the default theme. */
    LOADING,

    /** First run, or a run that never finished setting up. */
    ONBOARDING,

    /** The tabs and the whole navigation graph. */
    MAIN,
}

/**
 * Top-level app state: theme, currency, and whether onboarding is done. `null` means settings are
 * still loading (show a spinner under the default theme).
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class RootViewModel @Inject constructor(
    settings: SettingsRepository,

    @param:DeviceLocales private val deviceLocales: List<Locale>,
    private val maintenance: Maintenance,
    // In [ApplicationScope] so the pass outlives this view model, and starts from the UI coming up rather
    // than from whichever screen happened to be showing.
    @param:ApplicationScope private val appScope: CoroutineScope,
    /** Whether the app is open behind its lock — the activity reports the app coming and going to it. */
    val appLock: AppLock,
    /** The lesson setup asked to book, for the app to open as it comes up. */
    val firstLesson: FirstLesson,
) : ViewModel() {

    /** Catch up **in process**, not by enqueueing a worker — called once by `AppRoot` as the UI comes up. */
    fun catchUp() {
        appScope.launch { runCatchingCancellable { maintenance.run() } }
    }

    val settings: StateFlow<AppSettings?> = settings.observe()
        .onEach { AppLocale.current = it.language.localeFor(deviceLocales) }
        .hold(this, null)

    /** What to show for [settings], whatever state they are in. */
    fun routeFor(settings: AppSettings?): RootRoute = when {
        settings == null -> RootRoute.LOADING
        !settings.onboardingComplete -> RootRoute.ONBOARDING
        else -> RootRoute.MAIN
    }

    /**
     * Which locale to display [settings] in — the strings, their plural rules, the month names and the
     * decimal separator, which all have to be the same one.
     */
    fun localeFor(settings: AppSettings?): Locale =
        (settings?.language ?: AppLanguage.SYSTEM).localeFor(deviceLocales)
}
