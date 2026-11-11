package com.seblful.klas.ui

import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.lock.AppLock
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppSessionTest {

    @get:Rule
    val env = TestEnv()

    private val time = TestTimeSource()
    private val lock = AppLock(time)
    private val session = AppSession(
        env.settingsRepo,
        DisplayLocale(env.settingsRepo, env.deviceLocales.toDevicePreferences()),
        lock,
    )

    private class RecordingHost : HostEffects {
        val hidden = mutableListOf<Boolean>()
        val shortcutTitles = mutableListOf<String>()

        override fun hideFromSwitcher(hide: Boolean) {
            hidden += hide
        }

        override fun publishShortcuts(texts: Texts) {
            shortcutTitles += texts.string(Strings.nav_today)
        }
    }

    private suspend fun privacy(change: Privacy.() -> Privacy) =
        env.settingsRepo.update { it.copy(privacy = it.privacy.change()) }

    @Test
    fun `the switcher hides the app when either the setting or the lock asks`() = runTest {
        val host = RecordingHost()
        val applying = launch { session.keepApplied(host) }
        advanceUntilIdle()

        privacy { copy(hideInRecents = true) }
        advanceUntilIdle()
        privacy { copy(hideInRecents = false, appLock = true) }
        advanceUntilIdle()
        privacy { copy(appLock = false) }
        advanceUntilIdle()
        applying.cancel()

        assertEquals("the lock keeps it hidden through the swap", listOf(false, true, false), host.hidden)
    }

    @Test
    fun `the icon's shortcuts follow the app's language, not the phone's`() = runTest {
        val host = RecordingHost()
        val applying = launch { session.keepApplied(host) }
        advanceUntilIdle()

        env.settingsRepo.update { it.copy(language = AppLanguage.ENGLISH) }
        advanceUntilIdle()
        applying.cancel()

        assertEquals(
            listOf(Texts.of("ru").string(Strings.nav_today), Texts.of("en").string(Strings.nav_today)),
            host.shortcutTitles,
        )
    }

    @Test
    fun `coming back relocks after the stored delay`() = runTest {
        privacy { copy(appLock = true, relockAfterMinutes = 5) }
        env.settingsRepo.get()
        lock.unlocked()

        session.backgrounded()
        time += 4.minutes
        session.foregrounded(canUnlock = true)
        assertTrue("back within the delay", lock.open.value)

        session.backgrounded()
        time += 5.minutes
        session.foregrounded(canUnlock = true)
        assertFalse("away for the whole delay", lock.open.value)
    }
}
