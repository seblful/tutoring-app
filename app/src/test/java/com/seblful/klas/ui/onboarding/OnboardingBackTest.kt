package com.seblful.klas.ui.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.MainActivity
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.store.StoreScreenshots
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import dev.zacsweers.metro.createGraphFactory
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Moving through first run, which sits outside `NavDisplay` and so handles the system back gesture, and draws
 * its steps' arrivals, itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class, qualifiers = "en-rUS-" + StoreScreenshots.PHONE)
class OnboardingBackTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun freshInstall() {
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        app.graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = DayOfWeek.MONDAY,
            deviceLocales = listOf(Locale.US),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
    }

    @Test
    fun `system back steps back through setup and leaves from the welcome step`() = onLocaleStep { activity ->
        back(activity)
        waitFor(Strings.onboarding_start)
        assertFalse(activity.isFinishing)

        back(activity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a held back gesture shrinks the step and a cancelled one restores it`() = onLocaleStep { activity ->
        val resting = bounds(Strings.onboarding_locale_desc)

        gesture(activity) { it.dispatchOnBackStarted(event(0f)); it.dispatchOnBackProgressed(event(0.5f)) }
        val held = bounds(Strings.onboarding_locale_desc)
        assertTrue("narrower while held: $resting → $held", held.width < resting.width)
        assertTrue("drifts away from the left edge: $resting → $held", held.center.x > resting.center.x)

        gesture(activity) { it.dispatchOnBackCancelled() }
        assertEquals(resting, bounds(Strings.onboarding_locale_desc))
        compose.onNodeWithText(text(Strings.onboarding_locale_title)).assertExists()
    }

    @Test
    fun `the next step arrives by fade through rather than in one frame`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(Strings.onboarding_start)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(text(Strings.onboarding_start)).performClick()
            compose.mainClock.advanceTimeBy(48)

            // The welcome step is still fading out; the next waits at the scale it will grow from.
            compose.onNodeWithText(text(Strings.onboarding_start)).assertExists()
            val arriving = bounds(Strings.onboarding_locale_desc)

            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText(text(Strings.onboarding_start)).assertDoesNotExist()
            val landed = bounds(Strings.onboarding_locale_desc)
            assertTrue("arriving smaller: $arriving, then $landed", arriving.width < landed.width)
        }
    }

    @Test
    fun `a released back gesture fades the step from where it was dragged`() = onLocaleStep { activity ->
        val resting = bounds(Strings.onboarding_locale_desc)
        gesture(activity) { it.dispatchOnBackStarted(event(0f)); it.dispatchOnBackProgressed(event(0.5f)) }

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)

        // Leaving, still shrunk as the finger left it, over the welcome step already there beneath.
        val leaving = bounds(Strings.onboarding_locale_desc)
        assertTrue("held as dragged: $resting → $leaving", leaving.width < resting.width)
        compose.onNodeWithText(text(Strings.onboarding_start)).assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(text(Strings.onboarding_locale_desc)).assertDoesNotExist()
        assertFalse(activity.isFinishing)
    }

    private fun onLocaleStep(test: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: ComponentActivity
            scenario.onActivity { activity = it }
            waitFor(Strings.onboarding_start)
            compose.onNodeWithText(text(Strings.onboarding_start)).performClick()
            waitFor(Strings.onboarding_locale_desc)
            test(activity)
        }
    }

    private fun back(activity: ComponentActivity) = gesture(activity) { it.onBackPressed() }

    private fun gesture(activity: ComponentActivity, dispatch: (OnBackPressedDispatcher) -> Unit) {
        compose.runOnIdle { dispatch(activity.onBackPressedDispatcher) }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private fun bounds(res: StringResource): Rect =
        compose.onNodeWithText(text(res)).fetchSemanticsNode().boundsInRoot

    private fun waitFor(res: StringResource) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text(res)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun text(res: StringResource) = ApplicationProvider.getApplicationContext<StoreApp>().texts().string(res)
}
