package com.seblful.klas.screenshot

import androidx.annotation.StringRes
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * The real app, seeded with the demo tutor, on each window class it adapts to: the bar or the rail, and
 * the People list alone or with a card beside it. Goldens sit with the preview goldens and are recorded and
 * compared by the same Roborazzi tasks (see `docs/testing.md`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = StoreApp::class)
class FormFactorScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val tutor = DemoTutor.english
    private val timeZone = TimeZone.getDefault()
    private val locale = Locale.getDefault()

    @Before
    fun seed() {
        TimeZone.setDefault(TimeZone.getTimeZone(DemoTutor.ZONE.id))
        Locale.setDefault(tutor.locale)
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        val graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = tutor.firstDayOfWeek,
            deviceLocales = listOf(tutor.locale),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        app.graph = graph
        runBlocking { graph.seeder.seed(tutor) }
    }

    @After
    fun restoreDefaults() {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
    fun phone() = tour("phone")

    @Test
    @Config(qualifiers = "en-rUS-w673dp-h841dp-port-mdpi")
    fun foldable() = tour("foldable")

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-mdpi")
    fun tablet() = tour("tablet")

    @Test
    @Config(qualifiers = "en-rUS-w1920dp-h1080dp-land-mdpi")
    fun desktop() = tour("desktop")

    // Ends on the card: a tab tapped before its bar hides and returns may still show a press, so no capture
    // follows a return to the tabs.
    private fun tour(formFactor: String) {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The schedule is not captured: its day is one column at any width, and its «now» line arrives
            // on its own flow, at a moment Robolectric does not pin down.
            waitFor(string(R.string.class_status_in_progress))

            tab(R.string.nav_homework)
            waitFor(tutor.group.homework)
            capture(formFactor, "homework")

            tab(R.string.nav_people)
            waitFor(tutor.debtor.name)
            capture(formFactor, "people")

            compose.onAllNodesWithText(tutor.debtor.name, substring = true)
                .filterToOne(hasClickAction()).performClick()
            // «Earned» is the card's own; «Owes» is on the list beside it too.
            waitFor(string(R.string.stat_earned))
            capture(formFactor, "people-student")
        }
    }

    private fun capture(formFactor: String, screen: String) {
        settle()
        captureScreenRoboImage(
            "src/test/screenshots/FormFactor.$formFactor.$screen.png",
            RoborazziOptions(compareOptions = CompareOptions(resultValidator = ThresholdValidator(ChangeThreshold))),
        )
    }

    // A tab is reached the way the tutor reaches it, so on a phone it is only there while a tab is showing.
    private fun tab(@StringRes label: Int) {
        compose.onAllNodesWithText(string(label)).filterToOne(isSelectable()).performClick()
        settle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<StoreApp>().getString(id)

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 2_000L
    }
}
