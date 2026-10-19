package com.seblful.klas.ui

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.DeviceLocales
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.localeFor
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.work.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
    /** The phone's locales, for the half of [localeFor] that «follow the system» leaves open. */
    @param:DeviceLocales private val deviceLocales: List<Locale>,
    private val maintenance: Maintenance,
    // In [ApplicationScope] so the pass outlives this view model, and starts from the UI coming up rather
    // than from whichever screen happened to be showing.
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /**
     * Catch up **in process**, not by enqueueing a worker — called once by `AppRoot` as the UI comes up.
     *
     * This is what settles income: [Maintenance] auto-completes the lessons whose end time has passed, and
     * completing one is what bills it. Asking WorkManager to do that on the way in meant the schedule and
     * the reports the tutor was about to look at were as current as WorkManager felt like being — and on
     * an install the system has put in the restricted battery bucket, that can be much later or not at
     * all. Whereas the app *is* running, right here, and the work is ordinary suspend code over the
     * repositories.
     *
     * **Why here rather than `KlasApp.onCreate`, which is also "the app is running, right here".** Because
     * a started process is not proof the tutor is coming: starting a background worker starts the process
     * too, so the *unwatched* passes — the periodic one and the boot receiver, which announce what they
     * settle in the tray — ran this catch-up through `Application.onCreate` first and then found nothing
     * left to report. The lesson was already billed, and the notification that exists to say so never
     * came. "On the way in" means on the way into the UI, and that is exactly what calls this: the one
     * place that knows the tutor is about to look at the schedule themselves, and so the one pass that may
     * quietly leave the tray alone.
     *
     * Guarded because it is startup work: a failure is recorded rather than crashing the launch. The
     * periodic worker stays as the safety net for everything that happens while the app is closed.
     */
    fun catchUp() {
        appScope.launch { runCatchingCancellable { maintenance.run() } }
    }

    val settings: StateFlow<AppSettings?> = settings.observe()
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
     *
     * A row that has not loaded reads as [AppLanguage.SYSTEM], the default, so the frame before it arrives is
     * in the phone's language rather than in a language nobody asked for. It is a named answer for the reason
     * [routeFor] is: resolving «follow the system» is the one part a composable would otherwise decide, and
     * `AppRoot` may not be the second place that knows what SYSTEM means.
     */
    fun localeFor(settings: AppSettings?): Locale =
        (settings?.language ?: AppLanguage.SYSTEM).localeFor(deviceLocales)
}
