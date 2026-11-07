package com.seblful.klas.screenshot

import androidx.annotation.StringRes
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.format.formatDay
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
    private lateinit var graph: StoreGraph

    @Before
    fun seed() {
        TimeZone.setDefault(TimeZone.getTimeZone(DemoTutor.ZONE.id))
        Locale.setDefault(tutor.locale)
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        graph = createGraphFactory<StoreGraph.Factory>().create(
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

    // The demo tutor is seeded light, so dark is the tutor's own choice rather than the phone's.
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
    fun phoneDark() {
        runBlocking { graph.settings.update { it.copy(themeMode = ThemeChoice.DARK) } }
        tour("phone-dark")
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi", fontScale = 1.5f)
    fun phoneLargeText() = tour("phone-large-text")

    @Test
    @Config(qualifiers = "en-rUS-w673dp-h841dp-port-mdpi")
    fun foldable() = tour("foldable")

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-mdpi")
    fun tablet() = tour("tablet")

    @Test
    @Config(qualifiers = "en-rUS-w1920dp-h1080dp-land-mdpi")
    fun desktop() = tour("desktop")

    // A tab tapped before its bar hides and returns may still show a press, so no capture with the tabs
    // showing follows a return to them: the last one is a pushed page, which hides them.
    private fun tour(formFactor: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Neither the Today tab nor today's schedule is captured: their «now» arrives on its own flow, at a
            // moment Robolectric does not pin down. A later day of the month has none — and on a wide window the
            // month is where the schedule has two panes, the grid beside the day it opens.
            tab(R.string.nav_schedule)
            waitFor(string(R.string.class_status_in_progress))
            click(R.string.schedule_view_month)
            compose.onAllNodesWithText(LATER_DAY).filterToOne(isSelectable()).performClick()
            laterDayShown()
            settle()
            capture(formFactor, "schedule-month")

            tab(R.string.nav_homework)
            waitFor(tutor.group.homework)
            capture(formFactor, "homework")

            tab(R.string.nav_reports)
            // The income heads the report at every width; the debts are below the fold on a phone.
            waitFor(string(R.string.reports_total_income))
            capture(formFactor, "reports")

            tab(R.string.nav_people)
            waitFor(tutor.debtor.name)
            capture(formFactor, "people")

            compose.onAllNodesWithText(tutor.debtor.name, substring = true)
                .filterToOne(hasClickAction()).performClick()
            // The balance's payment button is the card's own, near its top; «Owes» is on the list beside it too.
            waitFor(string(R.string.payment_add_short))
            capture(formFactor, "people-student")

            // On a phone the card hides the list the groups are on; beside the list, Back would leave People.
            val groupsTab = compose.onAllNodesWithText(string(R.string.groups_title)).filter(hasClickAction())
            if (groupsTab.fetchSemanticsNodes().isEmpty()) back(scenario)
            click(R.string.groups_title)
            compose.onAllNodesWithText(tutor.group.name).filterToOne(hasClickAction()).performClick()
            // The earnings card is the group card's own, near its top.
            waitFor(string(R.string.stat_earned))
            capture(formFactor, "people-group")

            // Back again: on a phone the card's bar has no gear, People's does.
            back(scenario)
            gear()
            // The index arrives whole, after the page beside it on a wide window; its last row says it is in.
            waitFor(string(R.string.settings_diagnostics_none))
            click(R.string.settings_lessons_title)
            waitFor(string(R.string.settings_autocomplete))
            capture(formFactor, "settings-lessons")
        }
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** Settings, through the gear on the tab's bar. */
    private fun gear() {
        compose.onAllNodes(hasContentDescription(string(R.string.settings_title)) and hasClickAction())
            .onFirst().performClick()
        settle()
    }

    private fun click(@StringRes label: Int) {
        compose.onAllNodesWithText(string(label)).filterToOne(hasClickAction()).performClick()
        settle()
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
        compose.onAllNodes((hasText(string(label)) or hasContentDescription(string(label))) and isSelectable())
            .onFirst().performClick()
        settle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The later day's own header: it is the new week's read, which the test clock does not wait for. */
    private fun laterDayShown() = waitFor(
        LocalDate(DemoTutor.NOW.year, DemoTutor.NOW.month, LATER_DAY.toInt()).formatDay(),
    )

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        finishRipples()
        compose.waitForIdle()
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<StoreApp>().getString(id)

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 2_000L

        /** A day of the demo tutor's month after its pinned today, the 14th. */
        const val LATER_DAY = "20"
    }
}
