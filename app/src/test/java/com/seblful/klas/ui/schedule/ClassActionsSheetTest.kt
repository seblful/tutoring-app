package com.seblful.klas.ui.schedule

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import com.seblful.klas.ui.theme.klasSemantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class ClassActionsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val asked = mutableListOf<Any>()

    private fun show(status: LessonStatus, inTheme: @Composable () -> Unit = {}) {
        val klass = TutoringClass(
            id = 1, lessonId = 1, studentId = 1, subjectId = 1,
            start = Instant.parse("2026-06-01T10:00:00Z"), durationMinutes = 60, price = Money.ofMajor(25),
            status = status,
        )
        val row = ScheduleRow(ClassSession(null, listOf(klass)), title = "Olivia Brooks", subjectName = "Spanish")
        compose.setContent {
            KlasTheme {
                inTheme()
                ClassActionsSheet(
                    row = row,
                    phase = LessonPhase.UPCOMING,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    homework = emptyList(),
                    onHomeworkDone = { _, _ -> },
                    onEdit = { asked += "edit" },
                    onStatus = { asked += it },
                    onAttendance = { _, attended -> asked += attended },
                    onMove = { asked += "move" },
                    onDelete = {},
                    onDismiss = {},
                    onCorrectCompletion = { asked += "correct" },
                    onBookNext = { asked += "next" },
                )
            }
        }
    }

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun `a planned lesson leads with Taught, Reschedule and Edit, and can be cancelled from a row`() {
        show(LessonStatus.SCHEDULED)

        button("Taught").assertIsEnabled().performClick()
        button("Reschedule").performClick()
        button("Edit").performClick()
        compose.onNodeWithText("Cancel lesson").performScrollTo().performClick()
        compose.onNodeWithText("Book the next one like this").performScrollTo().performClick()

        assertEquals(listOf(LessonStatus.COMPLETED, "move", "edit", LessonStatus.CANCELLED, "next"), asked)
        compose.onNodeWithText("Return to planned").assertDoesNotExist()
    }

    @Test
    fun `a cancelled lesson says why it cannot be marked taught, and returns to planned from a row`() {
        show(LessonStatus.CANCELLED)

        button("Taught").assertIsNotEnabled()
        compose.onNodeWithText("Cancelled lessons go back to planned first.").assertExists()
        compose.onNodeWithText("Return to planned").performScrollTo().performClick()

        assertEquals(listOf<Any>(LessonStatus.SCHEDULED), asked)
        compose.onNodeWithText("Cancel lesson").assertDoesNotExist()
    }

    @Test
    fun `a taught lesson keeps its spent tick and goes back to planned through the correction`() {
        show(LessonStatus.COMPLETED)

        button("Taught").assertIsNotEnabled()
        compose.onNodeWithText("Return to planned").performScrollTo().performClick()

        assertEquals(listOf<Any>("correct"), asked)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w360dp", fontScale = 1.5f)
    fun `at large text the three buttons wrap rather than squeeze a label`() {
        show(LessonStatus.SCHEDULED)

        val height = button("Reschedule").getUnclippedBoundsInRoot().height
        listOf("Taught", "Edit").forEach { label ->
            assertEquals(label, height, button(label).getUnclippedBoundsInRoot().height)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `cancelling is drawn red on the sheet, as deleting is`() {
        var red = Color.Unspecified
        show(LessonStatus.SCHEDULED) { red = klasSemantics.destructive }

        val row = compose.onNodeWithText("Cancel lesson").performScrollTo().captureToImage().toPixelMap()
        val near = { c: Color -> abs(c.red - red.red) < 0.08f && abs(c.green - red.green) < 0.08f }
        assertTrue((0 until row.width).any { x -> (0 until row.height).any { y -> near(row[x, y]) } })
    }

    @Test
    fun `attendance is one value row whose menu changes it`() {
        show(LessonStatus.SCHEDULED)

        compose.onNodeWithText("Attendance").performScrollTo().performClick()
        compose.onNodeWithText("Absent, no charge").performClick()

        assertEquals(listOf<Any>(Attended.ABSENT), asked)
    }
}
