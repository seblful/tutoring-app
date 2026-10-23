package com.seblful.klas.ui.schedule

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import java.util.Locale
import java.util.TimeZone

/**
 * The real app: a lesson cancelled from its sheet, or held and cancelled from the bar, reads as cancelled. The
 * rows are read, not the messages, which time out on the test clock (what is said is [LessonBatchTest]'s).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class, qualifiers = "en-rUS-w412dp-h915dp-port")
class CancelLessonFlowTest {

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
    fun restore() {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }

    @Test
    fun `a planned lesson cancelled from its sheet is cancelled`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openDay(LATER_DAY)
            lesson(R.string.class_status_scheduled).performClick()
            compose.waitForIdle()

            compose.onAllNodesWithText(string(R.string.class_cancel_lesson)).onFirst().performScrollTo().performClick()

            waitFor(string(R.string.class_status_cancelled))
        }
    }

    /** It did nothing at all: the bar's cancel took only planned lessons, and said nothing when that was none. */
    @Test
    fun `a taught lesson held and cancelled from the bar is cancelled`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openDay(EARLIER_DAY)
            lesson(R.string.class_status_completed).performTouchInput { longClick() }
            compose.waitForIdle()

            compose.onNodeWithContentDescription(string(R.string.batch_cancel)).performClick()

            waitFor(string(R.string.class_status_cancelled))
        }
    }

    @Test
    fun `a cancelled lesson held returns to the plan from the same place in the bar`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openDay(EARLIER_DAY)
            lesson(R.string.class_status_completed).performTouchInput { longClick() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(string(R.string.batch_cancel)).performClick()
            waitFor(string(R.string.class_status_cancelled))
            // Held by its action rather than a press: the Undo message lies over the row.
            lesson(R.string.class_status_cancelled).performSemanticsAction(SemanticsActions.OnLongClick)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(string(R.string.class_correct_planned)).performClick()

            // Planned again, and over: it asks to be marked.
            waitFor(string(R.string.class_status_overdue))
        }
    }

    private fun openDay(day: String) {
        compose.onAllNodesWithText(string(R.string.nav_schedule)).filterToOne(isSelectable()).performClick()
        compose.waitForIdle()
        click(R.string.schedule_view_month)
        compose.onAllNodesWithText(day).filterToOne(isSelectable()).performClick()
        compose.waitForIdle()
    }

    private fun lesson(@StringRes status: Int) =
        compose.onAllNodes(hasText(string(status), substring = true) and hasClickAction()).onFirst()

    private fun click(@StringRes label: Int) {
        compose.onAllNodesWithText(string(label)).filterToOne(hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<StoreApp>().getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** A day after the demo tutor's pinned today, with one planned lesson. */
        const val LATER_DAY = "20"

        /** The day before it, with one lesson taught. */
        const val EARLIER_DAY = "13"
    }
}
