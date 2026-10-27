package com.seblful.klas.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.guide.FirstStep
import com.seblful.klas.domain.guide.FirstSteps
import com.seblful.klas.domain.guide.PhoneSetup
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.notifications.RecentMarks
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class TodayContentTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private fun row(
        id: Long,
        who: String,
        hourUtc: Int,
        phase: LessonPhase,
        status: LessonStatus = LessonStatus.SCHEDULED,
    ) =
        TodayLessonRow(
            session = ClassSession(
                null,
                listOf(
                    TutoringClass(
                        id = id, lessonId = id, studentId = id, subjectId = 1,
                        start = Instant.parse("2026-06-01T%02d:00:00Z".format(hourUtc)), durationMinutes = 60,
                        price = Money.ofMajor(25), status = status,
                    ),
                ),
            ),
            who = who,
            subject = "Spanish",
            phase = phase,
        )

    private val asked = mutableListOf<String>()

    private fun show(state: TodayState) = compose.setContent {
        KlasTheme {
            TodayContent(
                state = state,
                actions = TodayActions(
                    openLesson = { asked += "open $it" },
                    markTaught = { asked += "mark ${it.who}" },
                    undoMark = { asked += "undo ${it.who}" },
                    hideFirstSteps = { asked += "hide" },
                    firstStep = { step, student -> asked += "$step $student" },
                    hidePhoneSetup = { asked += "hide phone" },
                    phoneStep = { asked += "$it" },
                ),
            )
        }
    }

    private fun state(lessons: List<TodayLessonRow>, recent: List<TodayMark> = emptyList()) = TodayState(
        lessons = lessons,
        outstanding = Reports.Outstanding(students = 0, total = Money.ZERO),
        homework = emptyList(),
        recentMarks = recent,
        lastBackupAt = null,
        backupStale = false,
    )

    @Test
    fun `each lesson has the round mark-done button, named for it - off until it has begun, a tick once taught`() {
        show(
            state(
                listOf(
                    row(1, "Emma Wilson", 9, LessonPhase.SETTLED, LessonStatus.COMPLETED),
                    row(2, "Olivia Brooks", 12, LessonPhase.IN_PROGRESS),
                    row(3, "Noah Bennett", 16, LessonPhase.UPCOMING),
                ),
            ),
        )

        compose.onNodeWithContentDescription("Mark Olivia Brooks,", substring = true).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Mark Noah Bennett,", substring = true).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mark Emma Wilson,", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Taught"))
            .performClick()

        assertEquals(listOf("mark Olivia Brooks", "open 1"), asked)
    }

    @Test
    fun `a lesson on now says so in a pill of its own, as every lesson row's status is`() {
        show(state(listOf(row(2, "Olivia Brooks", 12, LessonPhase.IN_PROGRESS))))

        compose.onAllNodesWithText("On now", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("· On now", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the first steps tick what is done and open the rest`() {
        val steps = FirstSteps(setOf(FirstStep.BOOK, FirstStep.REMINDERS))
        show(state(emptyList()).copy(firstSteps = FirstStepsCard(steps, student = 7)))

        compose.onNodeWithText("2 of 5").assertExists()
        compose.onNodeWithText("Book your first lesson")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
        compose.onNodeWithText("Pick a day and a time").assertDoesNotExist()
        compose.onNodeWithText("Record a payment")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not done yet"))
            .performClick()
        compose.onNodeWithText("Hide").performClick()

        assertEquals(listOf("PAYMENT 7", "hide"), asked)
    }

    @Test
    fun `a restored phone is asked only for what its old phone had - ticked and opened like the first steps`() {
        val setup =
            PhoneSetup(wanted = setOf(PhoneStep.REMINDERS, PhoneStep.WEEKLY_COPY), done = setOf(PhoneStep.REMINDERS))
        show(state(emptyList()).copy(phoneSetup = setup))

        compose.onNodeWithText("Set up this phone").assertExists()
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithText("Turn on calendar sync").assertDoesNotExist()
        compose.onNodeWithText("Turn on lesson reminders")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
        compose.onNodeWithText("Save a copy every week").performClick()
        compose.onNodeWithText("Pick the folder on this phone").assertExists()
        compose.onNodeWithText("Hide").performClick()

        assertEquals(listOf("WEEKLY_COPY", "hide phone"), asked)
    }

    @Test
    fun `with every step done the card says so and closes`() {
        show(state(emptyList()).copy(firstSteps = FirstStepsCard(FirstSteps(FirstStep.entries.toSet()), student = 7)))

        compose.onNodeWithText("You're all set").assertExists()
        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("hide"), asked)
    }

    @Test
    fun `the next lesson's card gives the name a line of its own, not one after the time`() {
        show(state(listOf(row(3, "Noah Bennett", 16, LessonPhase.UPCOMING))))

        // The card's line and the day's row.
        compose.onAllNodesWithText("Noah Bennett").assertCountEquals(2)
        compose.onAllNodesWithText("· Noah Bennett", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the next lesson's Open sits by its label, leaving the time and name the card's whole width`() {
        show(state(listOf(row(3, "Noah Bennett", 16, LessonPhase.UPCOMING))))

        val open = compose.onNodeWithText("Open").getUnclippedBoundsInRoot()
        val time = compose.onAllNodesWithText("–", substring = true, useUnmergedTree = true).onFirst()
            .getUnclippedBoundsInRoot()
        assertTrue("Open is beside the time", open.bottom <= time.top)
    }

    @Test
    fun `a mark made outside the app is listed with its undo`() {
        val taught = row(2, "Olivia Brooks", 12, LessonPhase.SETTLED, LessonStatus.COMPLETED)
        val mark = RecentMarks.Mark(taught.session, Instant.parse("2026-06-01T13:02:00Z"))
        show(state(listOf(taught), recent = listOf(TodayMark(mark, "Olivia Brooks"))))

        compose.onNodeWithText("Marked outside the app").assertExists()
        compose.onNodeWithContentDescription("Undo").performClick()

        assertEquals(listOf("undo Olivia Brooks"), asked)
    }

    @Test
    fun `the day's last row ends clear of the Book button, not under it`() {
        val lessons = (1..8).map { row(it.toLong(), "Student $it", 8 + it, LessonPhase.UPCOMING) }
        compose.setContent {
            KlasTheme {
                Box(Modifier.height(400.dp).testTag("screen")) {
                    TodayContent(state = state(lessons), actions = TodayActions())
                }
            }
        }

        repeat(4) { compose.onNodeWithTag("screen").performTouchInput { swipeUp() } }

        val last = compose.onNodeWithText("Student 8").getUnclippedBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        val gap = screen.bottom - last.bottom
        assertTrue("the last row ends $gap above the bottom", gap >= FabClearance)
    }
}
