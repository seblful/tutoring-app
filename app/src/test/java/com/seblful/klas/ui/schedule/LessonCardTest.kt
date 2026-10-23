package com.seblful.klas.ui.schedule

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class LessonCardTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private fun klass(id: Long, student: Long, groupId: Long? = null) = TutoringClass(
        id = id, lessonId = 1, studentId = student, subjectId = 1,
        start = Instant.parse("2026-06-01T10:00:00Z"), durationMinutes = 60, price = Money.ofMajor(30),
        groupId = groupId,
    )

    private fun show(vararg rows: ScheduleRow) = compose.setContent {
        KlasTheme {
            LazyColumn {
                rows.forEach { row ->
                    item {
                        SwipeToCompleteCard(row, LessonPhase.UPCOMING, 48.dp, onClick = {}, onComplete = {})
                    }
                    // The same lesson as a slice of its day's section, as Week and Month draw it.
                    item {
                        SwipeToCompleteCard(
                            row, LessonPhase.UPCOMING, 48.dp, onClick = {}, onComplete = {},
                            slice = SlicePosition.LAST,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `only the group lesson carries the kind icon, as a card and as a slice`() {
        show(
            ScheduleRow(ClassSession(null, listOf(klass(1, student = 1))), title = "Emma Wilson", subjectName = "Math"),
            ScheduleRow(
                ClassSession(7, listOf(klass(2, student = 2, groupId = 7), klass(3, student = 3, groupId = 7))),
                title = "SAT Math Prep",
                subjectName = "Math",
                members = listOf("Mia", "Lucas"),
            ),
        )

        compose.onAllNodesWithContentDescription("Individual").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Group").assertCountEquals(2)
    }
}
