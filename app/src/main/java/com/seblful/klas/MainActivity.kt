package com.seblful.klas

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.seblful.klas.di.appGraph
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.deviceLocales
import com.seblful.klas.format.inLanguage
import com.seblful.klas.format.localeFor
import com.seblful.klas.lock.canUnlock
import com.seblful.klas.lock.unlockPrompt
import com.seblful.klas.notifications.AppShortcuts
import com.seblful.klas.text.Texts
import com.seblful.klas.ui.AppRoot
import com.seblful.klas.ui.RootViewModel
import com.seblful.klas.ui.common.LocalSearchKey
import com.seblful.klas.ui.common.SearchKey
import com.seblful.klas.ui.navigation.KeyboardShortcuts
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The one activity — a [FragmentActivity] because the app lock's [BiometricPrompt] needs one. */
class MainActivity : FragmentActivity() {
    private val searchKey = SearchKey()

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
        appGraph.appLanguages.start()

        /*
         * The splash stays up until the settings row has arrived, which is what makes the handover
         * silent. `AppRoot` resolves the theme from that row, so lifting the splash any earlier shows
         * either a spinner or — for a tutor who has forced a theme against their phone's — a frame of
         * the wrong one. Room emits the row (there is always exactly one, seeded on open), so this
         * condition always resolves; if it somehow did not, the platform lifts the splash anyway rather
         * than letting an app hold it indefinitely.
         */
        splash.setKeepOnScreenCondition { rootViewModel.settings.value == null }
        splash.setOnExitAnimationListener { provider ->
            fadeThrough(provider.view, provider.iconView, findViewById(android.R.id.content), provider::remove)
        }

        enableEdgeToEdge()
        // The tab bar draws behind 3-button navigation; the default scrim would tint it. Icon contrast is
        // `KlasTheme`'s job.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            // Every `metroViewModel()` below finds the graph's factory here.
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
                LocalSearchKey provides searchKey,
            ) {
                // Theme is applied inside AppRoot, driven by the user's theme setting.
                AppRoot(rootViewModel, onUnlock = ::promptUnlock)
            }
        }
        // The launcher's shortcuts, in the app's language — again whenever that changes.
        lifecycleScope.launch {
            rootViewModel.settings
                .map { rootViewModel.languageFor(it) }
                .distinctUntilChanged()
                .collect { AppShortcuts.publish(this@MainActivity, Texts.of(it)) }
        }
        // Whatever the app lock hides must not show in recent apps either, or the lock hides nothing.
        lifecycleScope.launch {
            rootViewModel.settings
                .map { it?.privacy?.let { privacy -> privacy.hideInRecents || privacy.appLock } == true }
                .distinctUntilChanged()
                .collect(::hideFromRecents)
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { appGraph.appLanguages.refresh() }
        rootViewModel.appLock.foregrounded(rootViewModel.settings.value?.privacy?.relockAfterMinutes, canUnlock())
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) rootViewModel.appLock.backgrounded()
    }

    /**
     * A Ctrl-chord nothing on screen took — see [KeyboardShortcuts]. Handed to the same listener a tray
     * tap reaches, so a chord goes where that tap would.
     */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val action = KeyboardShortcuts.actionFor(keyCode, event.isCtrlPressed)
            ?: return super.onKeyShortcut(keyCode, event)
        onNewIntent(Intent(action, null, this, MainActivity::class.java))
        return true
    }

    /**
     * A bare key nothing on screen took — a text field takes its own: `/` opens the search in front, and
     * [KeyboardShortcuts.keys] go where their tray entries do.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = event.hasNoModifiers() && when (val action = KeyboardShortcuts.keyActionFor(keyCode)) {
            null -> keyCode == KeyEvent.KEYCODE_SLASH && searchKey.press()
            else -> {
                onNewIntent(Intent(action, null, this, MainActivity::class.java))
                true
            }
        }
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += KeyboardShortcuts.group(Texts.of(resources.configuration.locales[0].toLanguageTag()))
    }

    private fun promptUnlock(title: String) {
        val lock = rootViewModel.appLock
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = lock.unlocked()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = lock.prompting(false)
            },
        )
        lock.prompting(true)
        prompt.authenticate(unlockPrompt(title))
    }

    /** A blank preview in recent apps: its own switch from Android 13, the secure flag (no screenshots) before. */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
