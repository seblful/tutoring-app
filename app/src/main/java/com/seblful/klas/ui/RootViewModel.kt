package com.seblful.klas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.ui.common.hold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
 * Which theme the tutor asked for. The stored value is a string, and «follow the system» is a real
 * answer rather than the absence of one — so is *not yet loaded*, which also follows the system, since
 * the alternative is a flash of the wrong theme before the row arrives.
 */
enum class ThemeChoice {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        /** [SYSTEM] for anything unrecognised, and for a settings row that has not loaded. */
        fun parse(stored: String?): ThemeChoice = when (stored) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}

/**
 * Top-level app state: theme, currency, and whether onboarding is done. `null` means settings are
 * still loading (show a spinner under the default theme).
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.observe()
        .hold(this, null)

    /** What to show for [settings], whatever state they are in. */
    fun routeFor(settings: AppSettings?): RootRoute = when {
        settings == null -> RootRoute.LOADING
        !settings.onboardingComplete -> RootRoute.ONBOARDING
        else -> RootRoute.MAIN
    }
}
