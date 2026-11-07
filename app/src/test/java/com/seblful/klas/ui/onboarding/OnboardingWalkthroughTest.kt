package com.seblful.klas.ui.onboarding

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.Money
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.store.StoreScreenshots
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** First run walked from the welcome step to «Done», as a new tutor sees it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class, qualifiers = "en-rUS-" + StoreScreenshots.PHONE)
class OnboardingWalkthroughTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var graph: StoreGraph

    @Before
    fun freshInstall() {
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = DayOfWeek.MONDAY,
            deviceLocales = listOf(Locale.US),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        app.graph = graph
    }

    @Test
    fun `setup asks for the basics, a subject and a priced student - and nothing else`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            tap(R.string.onboarding_start)

            // The basics: the lesson length sits beside the language and currency, and is stored as tapped.
            waitFor(R.string.settings_default_lesson)
            tap(R.string.hours_one_and_half)
            stored { graph.settings.get().defaultLessonMinutes == 90 }
            tap(R.string.onboarding_continue)

            // A suggested subject is one tap, and on while it exists.
            waitFor(R.string.onboarding_subjects_desc)
            compose.onNodeWithText(MATH).performClick()
            stored { graph.subjects.observeAll().first().isNotEmpty() }
            mathShown(selected = true)
            compose.onAllNodesWithText(MATH).onFirst().assertIsSelected()
            tap(R.string.onboarding_continue)

            // The student: a name and the only subject's price, already ticked; no contact fields.
            waitFor(R.string.onboarding_student_desc)
            compose.onNodeWithText(text(R.string.student_phone_label)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.student_email_label)).assertDoesNotExist()
            compose.onNodeWithText(MATH).assertIsOn()
            compose.onNodeWithText(text(R.string.student_name_label)).performTextInput("Anna")
            compose.onNodeWithText(text(R.string.price_label)).performTextInput("30")
            // Said per lesson of the length picked on the first step: 30 an hour is 45 for 90 minutes.
            compose.onNodeWithText("45", substring = true).assertExists()
            compose.onNodeWithText(text(R.string.onboarding_finish)).assertIsEnabled()
            tap(R.string.onboarding_finish)

            // The student is in; their first lesson is offered, and booking it is where setup gives way to the
            // app — on the booking form, for them.
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithText(text(R.string.onboarding_book_first)).fetchSemanticsNodes().isNotEmpty()
            }
            // A last screen, not a form: «All set» once, in its body, and «Not now» beside the booking, not also above.
            compose.onAllNodesWithText(text(R.string.onboarding_ready_title)).assertCountEquals(1)
            compose.onAllNodesWithText(text(R.string.onboarding_not_now)).assertCountEquals(1)
            tap(R.string.onboarding_book_first)
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithText(text(R.string.class_add_title)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Anna", substring = true).assertExists()
            assertTrue(runBlocking { graph.settings.get().onboardingComplete })
            val student = runBlocking { graph.students.observeActive().first() }.single()
            val math = runBlocking { graph.subjects.observeAll().first() }.single()
            assertEquals("Anna", student.name)
            assertEquals("", student.phone)
            assertEquals(Money.ofMajor(30), runBlocking { graph.enrollment.priceFor(student.id, math.id) }?.amount)
        }
    }

    @Test
    fun `a suggested subject tapped again is taken back`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            tap(R.string.onboarding_start)
            tap(R.string.onboarding_continue)
            waitFor(R.string.onboarding_subjects_desc)

            compose.onNodeWithText(MATH).performClick()
            stored { graph.subjects.observeAll().first().isNotEmpty() }
            mathShown(selected = true)
            compose.onAllNodesWithText(MATH).onFirst().performClick()
            stored { graph.subjects.observeAll().first().isEmpty() }

            mathShown(selected = false)
            compose.onNodeWithText(MATH).assertIsNotSelected()
            assertTrue(compose.onAllNodesWithText(text(R.string.action_undo)).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `a subject is typed in place, and setup can be skipped from any step`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            tap(R.string.onboarding_start)
            tap(R.string.onboarding_continue)
            waitFor(R.string.onboarding_subjects_desc)

            compose.onNodeWithText(text(R.string.subjects_add)).performTextInput("Astronomy")
            compose.onNodeWithText(text(R.string.subjects_add)).performImeAction()
            stored { graph.subjects.observeAll().first().map { it.name } == listOf("Astronomy") }

            tap(R.string.onboarding_skip)
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithText(text(R.string.onboarding_subjects_desc)).fetchSemanticsNodes().isEmpty()
            }
            assertTrue(runBlocking { graph.settings.get().onboardingComplete })
        }
    }

    /**
     * Waits until the database says [check] — idling the UI between looks, since a write's callback (the
     * snackbar after a removal, setup's own completion) runs on the main thread a bare read would block.
     */
    private fun stored(check: suspend () -> Boolean) = compose.waitUntil(TIMEOUT) {
        compose.waitForIdle()
        runBlocking { check() }
    }

    /**
     * Waits for the suggestion to show [selected]. The chip acts on what it shows, which trails the database:
     * tapped before it catches up, it adds the subject again instead of taking it back.
     */
    private fun mathShown(selected: Boolean) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(MATH).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Selected) == selected
    }

    private fun tap(res: Int) {
        waitFor(res)
        compose.onNodeWithText(text(res)).performClick()
        compose.waitForIdle()
    }

    private fun waitFor(res: Int) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text(res)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun text(res: Int) = ApplicationProvider.getApplicationContext<StoreApp>().getString(res)

    private companion object {
        const val TIMEOUT = 10_000L
        const val MATH = "Math"
    }
}
