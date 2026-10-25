package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.queries.groups.GroupLessonRow
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.groups.GroupLessonItem
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** A student's and a group's lesson rows say what the schedule says of a lesson over and unmarked. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class LessonRowBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val now = Instant.parse("2026-10-23T12:00:00Z")

    private fun lesson(id: Long, start: String, status: LessonStatus = LessonStatus.SCHEDULED) = TutoringClass(
        id = id, lessonId = id, studentId = 1, subjectId = 1, start = Instant.parse(start), durationMinutes = 60,
        price = Money.ofMajor(20), status = status,
    )

    private val past = lesson(1, "2026-10-21T15:00:00Z")
    private val ahead = lesson(2, "2026-10-24T11:00:00Z")
    private val taught = lesson(3, "2026-10-20T15:00:00Z", LessonStatus.COMPLETED)

    @Test
    fun `on a student's card, a past lesson nobody marked is «Not marked», not «Planned»`() {
        compose.setContent {
            KlasTheme {
                Column {
                    listOf(past, ahead, taught).forEach { LessonRow(ClassRow(it, "English"), now, onClick = {}) }
                }
            }
        }

        compose.onAllNodesWithText("Not marked").assertCountEquals(1)
        compose.onAllNodesWithText("Planned").assertCountEquals(1)
        compose.onAllNodesWithText("Taught").assertCountEquals(1)
    }

    @Test
    fun `on a group's card, the same`() {
        compose.setContent {
            KlasTheme {
                Column {
                    listOf(past, ahead).forEach {
                        GroupLessonItem(GroupLessonRow(ClassSession(1, listOf(it)), charge = null), now, onClick = {})
                    }
                }
            }
        }

        compose.onAllNodesWithText("Not marked").assertCountEquals(1)
        compose.onAllNodesWithText("Planned").assertCountEquals(1)
    }
}
