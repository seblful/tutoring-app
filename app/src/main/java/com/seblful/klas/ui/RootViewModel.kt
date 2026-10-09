package com.seblful.klas.ui

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.di.DeviceLocales
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.ui.common.state.hold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject

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
@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    /** The phone's locales, for the half of [localeFor] that «follow the system» leaves open. */
    @param:DeviceLocales private val deviceLocales: List<Locale>,
) : ViewModel() {
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
