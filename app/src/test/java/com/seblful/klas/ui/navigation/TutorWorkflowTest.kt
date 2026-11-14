package com.seblful.klas.ui.navigation

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.seblful.klas.MainActivity
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.store.StoreScreenshots
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.FakeProStore
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
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
        val subject = graph.subjects.createOrReuse("Maths")
        studentId =
            graph.enrollment.createStudent(Student(name = "Workflow student"), mapOf(subject to Money.ofMajor(30)))
        lessonId = graph.classes.book(
            subject, DemoTutor.CLOCK.now().minus(2.hours), 60, ClassMode.IN_PERSON,
            listOf(studentId), Money.ofMajor(30),
        ).written().single()
    }

    private fun text(res: StringResource) = ApplicationProvider.getApplicationContext<StoreApp>().texts().string(res)

    private fun selected(count: Int) = ApplicationProvider.getApplicationContext<StoreApp>().resources
        .texts().plural(Plurals.batch_selected, count, count)

    private fun waitFor(res: StringResource) {
        waitForText(text(res))
    }

    private fun waitForText(value: String) {
        // Generous: a shared CI runner can take over 10 s to draw a screen, and a pass returns at once.
        compose.waitUntil(30_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(res: StringResource) = compose.onNodeWithText(text(res)).performClick()

    private fun sheetClick(res: StringResource) = compose.onNodeWithText(text(res)).performScrollTo().performClick()

    @Test
    fun `lesson actions undo a completion and carry the student into the next booking`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_schedule)) and isSelectable()).performClick()
            waitForText("Workflow student")
            compose.onNodeWithText("Workflow student").performClick()
            compose.onNode(hasText(text(Strings.class_status_completed)) and hasClickAction()).performClick()
            waitFor(Strings.class_completed_message)
            click(Strings.action_undo)
            compose.waitUntil(5_000) {
                runBlocking { graph.classes.getById(lessonId)?.status == LessonStatus.SCHEDULED }
            }

            compose.onNodeWithText("Workflow student").performClick()
            // The sheet opens over a list the undo is still redrawing; on a slower machine it is not there yet.
            waitFor(Strings.homework_add)
            compose.onNodeWithText(text(Strings.homework_add)).performScrollTo().assertExists()
            compose.onNodeWithText(text(Strings.add_payment)).performScrollTo().assertExists()
            sheetClick(Strings.class_open_student)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(text(Strings.class_book)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(text(Strings.class_book)).performClick()
            waitFor(Strings.class_add_title)
            // The title is drawn while the form still loads; who and what come with the loaded form.
            waitForText("Workflow student")
            waitForText("Maths")
        }
    }

    @Test
    fun `a free tutor with five students is shown Klas Pro for a sixth and the form once bought`() {
        (graph.proStore as FakeProStore).sells = true
        runBlocking { repeat(4) { graph.enrollment.createStudent(Student(name = "Student $it"), emptyMap()) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_people)) and isSelectable()).performClick()
            // The add button is named for TalkBack once, by its description.
            val newStudent = text(Strings.fab_new_student)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription(newStudent).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(newStudent).performClick()
            waitFor(Strings.pro_buy_trial)
            assertTrue(compose.onAllNodesWithText(text(Strings.student_add_title)).fetchSemanticsNodes().isEmpty())

            click(Strings.pro_buy_trial)
            waitFor(Strings.student_add_title)
        }
        assertEquals(listOf(ProPlan.MONTHLY), (graph.proStore as FakeProStore).bought)
    }

    @Test
    fun `a student created from the group form returns through it to the original booking`() {
        runBlocking { graph.subjects.createOrReuse("Science") }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNodeWithContentDescription(text(Strings.class_book)).performClick()
            waitFor(Strings.class_add_title)
            // The title is drawn while the form still loads; the «Who» list comes with the loaded form, and
            // adding a group is in it.
            waitFor(Strings.class_who_label)
            click(Strings.class_who_label)
            waitFor(Strings.group_add_title)
            click(Strings.group_add_title)
            waitFor(Strings.group_name_label)
            compose.onNodeWithText(text(Strings.group_name_label)).performTextInput("After school")
            compose.onNodeWithText(text(Strings.group_price_label)).performTextReplacement("20")
            compose.onNodeWithText(text(Strings.group_subject_none)).performClick()
            compose.onNodeWithText("Science").performClick()

            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(Strings.schedule_add_student)))
            click(Strings.schedule_add_student)
            waitFor(Strings.student_add_title)
            compose.onNodeWithText(text(Strings.student_name_label)).performTextInput("New joiner")
            compose.onNodeWithText(text(Strings.price_label)).performScrollTo().performTextInput("35")
            click(Strings.student_form_add)
            waitFor(Strings.group_add_title)
            click(Strings.group_form_create)
            waitFor(Strings.class_add_title)
            waitForText("After school")
            waitForText("Science")
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
    fun `a long-press picks a lesson, and the bar marks the pick taught`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_schedule)) and isSelectable()).performClick()
            waitForText("Workflow student")
            compose.onNodeWithText("Workflow student").performTouchInput { longClick() }
            waitForText(selected(1))
            compose.onNodeWithContentDescription(text(Strings.batch_mark_taught)).performClick()
            compose.waitUntil(5_000) {
                runBlocking { graph.classes.getById(lessonId)?.status == LessonStatus.COMPLETED }
            }
            compose.onNodeWithText(selected(1)).assertDoesNotExist()
        }
    }

    @Test
    fun `a long-press picks homework, and the bar moves the pick to the archive`() {
        val given = runBlocking { graph.homework.give(Assignee.Student(studentId), "Workflow exercises") }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_homework)) and isSelectable()).performClick()
            waitForText("Workflow exercises")
            compose.onNodeWithText("Workflow exercises").performTouchInput { longClick() }
            waitForText(selected(1))
            compose.onNodeWithContentDescription(text(Strings.homework_batch_mark_done)).performClick()
            compose.waitUntil(5_000) {
                runBlocking { graph.homework.observeAll().first().single { it.id == given.id }.isDone }
            }
            compose.onNodeWithText(selected(1)).assertDoesNotExist()
        }
    }

    @Test
    fun `a student is corrected on a form of their own, saved from its bar`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_people)) and isSelectable()).performClick()
            waitForText("Workflow student")
            compose.onNodeWithText("Workflow student").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(text(Strings.action_edit)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(text(Strings.action_edit)).performClick()
            waitFor(Strings.student_edit_title)
            // The field is its own node only once the form has come in; under load the title shows first.
            val name = hasText("Workflow student") and hasSetTextAction()
            compose.waitUntil(5_000) { compose.onAllNodes(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(name).performTextReplacement("Workflow pupil")
            click(Strings.action_save)
            compose.waitUntil(5_000) {
                runBlocking { graph.students.getById(studentId)?.name == "Workflow pupil" }
            }
        }
    }

    @Test
    fun `a second tap on People brings a scrolled list back to the first student`() {
        runBlocking { repeat(PUPILS) { graph.students.upsert(Student(name = "Pupil %02d".format(it))) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            val people = hasText(text(Strings.nav_people)) and isSelectable()
            // The tabs come in once the settings are read, not with the first frame.
            compose.waitUntil(30_000) { compose.onAllNodes(people).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(people).performClick()
            waitForText("Pupil 00")
            compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Pupil", substring = true)))
                .performScrollToNode(hasText("Workflow student"))
            compose.onNodeWithText("Pupil 00").assertDoesNotExist()

            compose.onNode(people).performClick()

            waitForText("Pupil 00")
            compose.onNodeWithText("Pupil 00").assertIsDisplayed()
        }
    }

    @Test
    fun `the widget's today opens the Today tab`() {
        val today = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(TrayNavigation.ACTION_OPEN_TODAY)
        ActivityScenario.launch<MainActivity>(today).use {
            waitFor(Strings.today_lessons)
            compose.onNode(hasText(text(Strings.nav_today)) and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun `bare keys open today and the search of the tab in front`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Workflow student")
            compose.onNode(hasText(text(Strings.nav_people)) and isSelectable()).performClick()
            waitForText("Workflow student")
            fun press(keyCode: Int) = scenario.onActivity {
                it.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }

            press(KeyEvent.KEYCODE_SLASH)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(text(Strings.search_close)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(text(Strings.search_close)).performClick()

            press(KeyEvent.KEYCODE_T)
            waitFor(Strings.today_lessons)
            compose.onNode(hasText(text(Strings.nav_today)) and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun `a student link opens their profile on the People tab`() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("klas://student/$studentId"))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(link).use {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(text(Strings.action_edit)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("Workflow student").onFirst().assertExists()
            // The profile is pushed, and the phone's tabs leave with the screen; Back finds it was People's.
            Espresso.pressBack()
            compose.waitForIdle()
            compose.onNode(hasText(text(Strings.nav_people)) and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun `a cold start from the tray opens the lesson it names`() {
        ActivityScenario.launch<MainActivity>(openLesson()).use {
            waitFor(Strings.class_edit_title)
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
            compose.onNodeWithText(text(Strings.class_edit_title)).assertDoesNotExist()
        }
    }

    @Test
    fun `notification and widget arrivals preserve the typed booking until Back returns to it`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Workflow student")
            compose.onNodeWithContentDescription(text(Strings.class_book)).performClick()
            waitFor(Strings.class_add_title)
            // Price is folded to a line, drawn with the loaded form; the rate is typed once it is opened.
            waitFor(Strings.class_section_price)
            compose.onNodeWithText(text(Strings.class_section_price)).performScrollTo().performClick()
            compose.onNodeWithText(text(Strings.price_label)).performScrollTo().performTextReplacement("35")
            val arrivals = listOf(
                TrayNavigation.ACTION_OPEN_TODAY,
                TrayNavigation.ACTION_OPEN_DEBTS,
                // The widgets' shortcuts.
                TrayNavigation.ACTION_OPEN_HOMEWORK,
                TrayNavigation.ACTION_OPEN_PEOPLE,
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
                waitFor(Strings.class_add_title)
                waitForText("35")
            }
            assertTrue(runBlocking { graph.classes.getAll().size == 1 })
        }
    }

    private companion object {
        /** More than a phone shows, so the list has somewhere to scroll. */
        const val PUPILS = 40
    }
}
