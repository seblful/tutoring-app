package com.seblful.klas.ui.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.store.StoreScreenshots
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
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

/** The system back gesture through first run, which sits outside `NavDisplay` and so handles it itself. */
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
        waitFor(R.string.onboarding_start)
        assertFalse(activity.isFinishing)

        back(activity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a held back gesture shrinks the step and a cancelled one restores it`() = onLocaleStep { activity ->
        val resting = bounds(R.string.onboarding_locale_desc)

        gesture(activity) { it.dispatchOnBackStarted(event(0f)); it.dispatchOnBackProgressed(event(0.5f)) }
        val held = bounds(R.string.onboarding_locale_desc)
        assertTrue("narrower while held: $resting → $held", held.width < resting.width)
        assertTrue("drifts away from the left edge: $resting → $held", held.center.x > resting.center.x)

        gesture(activity) { it.dispatchOnBackCancelled() }
        assertEquals(resting, bounds(R.string.onboarding_locale_desc))
        compose.onNodeWithText(text(R.string.onboarding_locale_title)).assertExists()
    }

    private fun onLocaleStep(test: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: ComponentActivity
            scenario.onActivity { activity = it }
            waitFor(R.string.onboarding_start)
            compose.onNodeWithText(text(R.string.onboarding_start)).performClick()
            waitFor(R.string.onboarding_locale_desc)
            test(activity)
        }
    }

    private fun back(activity: ComponentActivity) = gesture(activity) { it.onBackPressed() }

    private fun gesture(activity: ComponentActivity, dispatch: (OnBackPressedDispatcher) -> Unit) {
        compose.runOnIdle { dispatch(activity.onBackPressedDispatcher) }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private fun bounds(res: Int): Rect =
        compose.onNodeWithText(text(res)).fetchSemanticsNode().boundsInRoot

    private fun waitFor(res: Int) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text(res)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun text(res: Int) = ApplicationProvider.getApplicationContext<StoreApp>().getString(res)
}
