package com.seblful.klas

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.seblful.klas.di.appGraph
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.deviceLocales
import com.seblful.klas.format.inLanguage
import com.seblful.klas.format.localeFor
import com.seblful.klas.ui.AppRoot
import com.seblful.klas.ui.RootViewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/** The one activity. */
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels { appGraph.metroViewModelFactory }

    /**
     * The activity's base context comes from the device configuration rather than from the application's,
     * so it is pinned the same way and for the same reason: a language the app is actually written in, so
     * the plural rules match the words. See [inLanguage].
     */
    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(newBase.inLanguage(AppLanguage.SYSTEM.localeFor(deviceLocales())))

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, as the library requires: it swaps the activity's theme from
        // `Theme.Klas.Starting` to that theme's `postSplashScreenTheme`.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        /*
         * The splash stays up until the settings row has arrived, which is what makes the handover
         * silent. `AppRoot` resolves the theme from that row, so lifting the splash any earlier shows
         * either a spinner or — for a tutor who has forced a theme against their phone's — a frame of
         * the wrong one. Room emits the row (there is always exactly one, seeded on open), so this
         * condition always resolves; if it somehow did not, the platform lifts the splash anyway rather
         * than letting an app hold it indefinitely.
         */
        splash.setKeepOnScreenCondition { rootViewModel.settings.value == null }

        enableEdgeToEdge()
        // The tab bar draws behind 3-button navigation; the default scrim would tint it. Icon contrast is
        // `KlasTheme`'s job.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            // Every `metroViewModel()` below finds the graph's factory here.
            CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
                // Theme is applied inside AppRoot, driven by the user's theme setting.
                AppRoot(rootViewModel)
            }
        }
    }
}
