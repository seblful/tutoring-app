package com.seblful.klas.ui.today

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.notifications.RecentMarks
import com.seblful.klas.testutil.AppLocaleRule
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
}
