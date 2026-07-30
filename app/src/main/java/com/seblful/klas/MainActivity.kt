package com.seblful.klas

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.seblful.klas.ui.AppRoot
import com.seblful.klas.ui.RootViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The one activity.
 *
 * **It asks for no permission on the way in.** The notification permission used to be requested here,
 * from `onCreate`, so a first launch put a system dialog over a screen the tutor had not seen yet —
 * about a feature they had not met, in the one moment they had the least reason to say yes. And it was
 * re-attempted on every launch until granted, which on Android 13+ silently stops working after two
 * refusals, leaving the app permanently unable to remind anybody with nothing on screen to say so.
 *
 * Reminders are asked for where reminders are, in **Ещё → Настройки** — see `SettingsScreen`, which also
 * shows the platform's answer rather than only the tutor's intent.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Held by the activity rather than obtained inside the composition, so the splash can be kept up
     * until it has an answer — see [onCreate]. `AppRoot` is called directly from `setContent`, so its
     * `ViewModelStoreOwner` is this activity: `hiltViewModel()` there and `viewModels()` here resolve to
     * the same instance, and the screen is not reading a second copy of the settings row.
     */
    private val rootViewModel: RootViewModel by viewModels()

    // The activity's base context comes from the device configuration, not from the application's, so
    // pinning it there is not enough — every screen reads its strings through this one. See [inRussian].
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

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
        setContent {
            // Theme is applied inside AppRoot, driven by the user's theme setting.
            AppRoot(rootViewModel)
        }
    }
}
