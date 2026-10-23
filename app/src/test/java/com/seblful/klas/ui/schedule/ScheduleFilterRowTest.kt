package com.seblful.klas.ui.schedule

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class ScheduleFilterRowTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val students = listOf(Student(id = 1, name = "Emma Wilson"))
    private val groups = listOf(Group(id = 7, name = "SAT Math Prep"))
    private val asked = mutableListOf<Any?>()

    private fun showAction(filter: ScheduleFilter = ScheduleFilter(), students: List<Student> = this.students) =
        compose.setContent {
            KlasTheme {
                ScheduleFilterAction(
                    filter = filter,
                    students = students,
                    groups = if (students.isEmpty()) emptyList() else groups,
                    onWho = { asked += it },
                    onStatus = { asked += it },
                )
            }
        }

    private fun filterIcon() = compose.onNodeWithContentDescription("Filter the schedule")

    @Test
    fun `the bar's filter offers students, then groups, then a status`() {
        showAction()

        filterIcon().performClick()
        compose.onNodeWithText("Whose lessons").assertExists()
        compose.onNodeWithText("SAT Math Prep").performClick()
        filterIcon().performClick()
        compose.onNodeWithText("Cancelled").performClick()

        assertEquals(listOf(ScheduleWho.Group(7, "SAT Math Prep"), LessonStatus.CANCELLED), asked)
    }

    @Test
    fun `with nobody to narrow to there is no filter to offer`() {
        showAction(students = emptyList())

        filterIcon().assertDoesNotExist()
    }

    @Test
    fun `unfiltered, the row under the pager holds only the summary`() {
        compose.setContent {
            KlasTheme { ScheduleFilterRow(ScheduleFilter(), onWho = {}, onStatus = {}) { Text("5 lessons") } }
        }

        compose.onNodeWithText("5 lessons").assertExists()
        compose.onNodeWithText("Everyone").assertDoesNotExist()
        compose.onNodeWithText("Any status").assertDoesNotExist()
    }

    @Test
    fun `each narrowing is a chip that names it and takes it off`() {
        compose.setContent {
            KlasTheme {
                ScheduleFilterRow(
                    filter = ScheduleFilter(ScheduleWho.Student(1, "Emma Wilson"), LessonStatus.COMPLETED),
                    onWho = { asked += "who $it" },
                    onStatus = { asked += "status $it" },
                )
            }
        }

        compose.onNodeWithText("Emma Wilson").assertIsSelected().performClick()
        compose.onNodeWithText("Taught").assertIsSelected().performClick()

        assertEquals(listOf<Any?>("who null", "status null"), asked)
    }
}
