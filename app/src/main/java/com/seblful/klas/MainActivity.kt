package com.seblful.klas

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seblful.klas.ui.AppRoot
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

    // The activity's base context comes from the device configuration, not from the application's, so
    // pinning it there is not enough — every screen reads its strings through this one. See [inRussian].
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme is applied inside AppRoot, driven by the user's theme setting.
            AppRoot()
        }
    }
}
