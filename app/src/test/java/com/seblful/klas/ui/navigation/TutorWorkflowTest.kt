package com.seblful.klas.ui.navigation

import android.content.Intent
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.notifications.TrayNavigation
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
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.Locale
import kotlin.time.Duration.Companion.hours

/**
 * Exercises the production navigation graph and real repositories together.
 *
 * Stops short of dialogs: under an empty rule driving the real activity, a dialog window never lets
 * Robolectric report Compose idle. What the dialogs save is held by the view-model tests instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class, qualifiers = "en-rUS-" + StoreScreenshots.PHONE)
class TutorWorkflowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var graph: StoreGraph
    private var studentId = 0L
    private var lessonId = 0L

    @Before
    fun seed() = runBlocking {
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
        graph.settings.update { it.copy(onboardingComplete = true, language = AppLanguage.ENGLISH) }
        val subject = graph.subjects.upsert(Subject(name = "Maths"))
        studentId =
            graph.enrollment.createStudent(Student(name = "Workflow student"), mapOf(subject to Money.ofMajor(30)))
        lessonId = graph.classes.book(
            subject, DemoTutor.CLOCK.now().minus(2.hours), 60, ClassMode.IN_PERSON,
            listOf(studentId), Money.ofMajor(30),
        ).written().single()
    }

    private fun text(res: Int) = ApplicationProvider.getApplicationContext<StoreApp>().getString(res)

    private fun waitFor(res: Int) {
        waitForText(text(res))
    }

    private fun waitForText(value: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(res: Int) = compose.onNodeWithText(text(res)).performClick()

    private fun sheetClick(res: Int) = compose.onNodeWithText(text(res)).performScrollTo().performClick()

    @Test
    fun `lesson actions undo a completion and carry the student into the next booking`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNodeWithText("Workflow student").performClick()
            compose.onNode(hasText(text(R.string.class_status_completed)) and isSelectable()).performClick()
            waitFor(R.string.class_completed_message)
            click(R.string.action_undo)
            compose.waitUntil(5_000) {
                runBlocking { graph.classes.getById(lessonId)?.status == LessonStatus.SCHEDULED }
            }

            compose.onNodeWithText("Workflow student").performClick()
            compose.onNodeWithText(text(R.string.homework_add)).performScrollTo().assertExists()
            compose.onNodeWithText(text(R.string.add_payment)).performScrollTo().assertExists()
            sheetClick(R.string.class_open_student)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(text(R.string.class_book)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(text(R.string.class_book)).performClick()
            waitFor(R.string.class_add_title)
            compose.onNodeWithText("Workflow student").assertExists()
            compose.onNodeWithText("Maths").assertExists()
        }
    }

    @Test
    fun `a student created from the group form returns through it to the original booking`() {
        runBlocking { graph.subjects.upsert(Subject(name = "Science")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNodeWithContentDescription(text(R.string.schedule_add_class)).performClick()
            waitFor(R.string.class_add_title)
            // The title is drawn while the form still loads; the «Who» list comes with the loaded form, and
            // adding a group is in it.
            waitFor(R.string.class_who_label)
            click(R.string.class_who_label)
            waitFor(R.string.group_add_title)
            click(R.string.group_add_title)
            waitFor(R.string.group_name_label)
            compose.onNodeWithText(text(R.string.group_name_label)).performTextInput("After school")
            compose.onNodeWithText(text(R.string.group_price_label)).performTextReplacement("20")
            compose.onNodeWithText(text(R.string.group_subject_none)).performClick()
            compose.onNodeWithText("Science").performClick()

            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.schedule_add_student)))
            click(R.string.schedule_add_student)
            waitFor(R.string.student_add_title)
            compose.onNodeWithText(text(R.string.student_name_label)).performTextInput("New joiner")
            compose.onNodeWithText(text(R.string.price_label)).performScrollTo().performTextInput("35")
            click(R.string.action_save)
            waitFor(R.string.group_add_title)
            click(R.string.action_save)
            waitFor(R.string.class_add_title)
            compose.onNodeWithText("After school").assertExists()
            compose.onNodeWithText("Science").assertExists()
            val saved = runBlocking { graph.groups.observeAll().first().single() }
            assertEquals(Money.ofMajor(20), saved.price)
            assertEquals(1, runBlocking { graph.groups.getMemberIds(saved.id).size })
        }
    }

    private fun openLesson(flags: Int = 0) =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(TrayNavigation.ACTION_OPEN_LESSON)
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, lessonId)
            .addFlags(flags)

    @Test
    fun `a cold start from the tray opens the lesson it names`() {
        ActivityScenario.launch<MainActivity>(openLesson()).use {
            waitFor(R.string.class_edit_title)
        }
    }

    /**
     * Up to Android 11, Back finishes the root activity; Recents then relaunches it with the intent that
     * started the task — here, an old notification's.
     */
    @Test
    fun `a relaunch from Recents does not replay the notification that started the task`() {
        ActivityScenario.launch<MainActivity>(openLesson(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).use {
            waitForText("Workflow student")
            compose.waitForIdle()
            compose.onNodeWithText(text(R.string.class_edit_title)).assertDoesNotExist()
        }
    }

    @Test
    fun `notification and widget arrivals preserve the typed booking until Back returns to it`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Workflow student")
            compose.onNodeWithContentDescription(text(R.string.schedule_add_class)).performClick()
            waitFor(R.string.class_add_title)
            // Price is folded to a line; the rate is typed once it is opened.
            compose.onNodeWithText(text(R.string.class_section_price)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.price_label)).performScrollTo().performTextReplacement("35")
            val arrivals = listOf(
                TrayNavigation.ACTION_OPEN_TODAY,
                TrayNavigation.ACTION_OPEN_DEBTS,
                // The widgets' shortcuts.
                TrayNavigation.ACTION_OPEN_HOMEWORK,
                TrayNavigation.ACTION_OPEN_PEOPLE,
                // What the widget offered before People, still routed.
                TrayNavigation.ACTION_ADD_STUDENT,
            )
            for (action in arrivals) {
                scenario.onActivity { activity ->
                    ReflectionHelpers.callInstanceMethod<Unit>(
                        activity, "onNewIntent",
                        ClassParameter.from(
                            Intent::class.java,
                            Intent(activity, MainActivity::class.java).setAction(action),
                        ),
                    )
                }
                compose.waitForIdle()
                Espresso.pressBack()
                waitFor(R.string.class_add_title)
                compose.onNodeWithText("35").assertExists()
            }
            assertTrue(runBlocking { graph.classes.getAll().size == 1 })
        }
    }
}
