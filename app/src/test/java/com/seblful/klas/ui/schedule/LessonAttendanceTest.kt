package com.seblful.klas.ui.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class LessonAttendanceTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @get:Rule(order = 0)
    val env = TestEnv()

    @Test
    fun `every member can be absent without losing history and a no-show can still be billed`() = runBlocking {
        val subject = env.fixtures.subject()
        val first = env.fixtures.pricedStudent("First", subject)
        val second = env.fixtures.pricedStudent("Second", subject)
        val group = env.fixtures.group(subjectId = subject, members = setOf(first, second))
        val ids = env.fixtures.completedLesson(
            subject, listOf(first, second), Instant.parse("2026-05-01T10:00:00Z"), groupId = group,
        )
        var row by mutableStateOf(
            ScheduleRow(env.classRepo.getSessionFor(ids.first())!!, "Group", "Maths", listOf("First", "Second")),
        )
        compose.setContent {
            KlasTheme {
                LessonAttendance(row) { klass, attended ->
                    runBlocking {
                        env.classRepo.setAttendance(klass, attended)
                        row = row.copy(session = env.classRepo.getSessionFor(ids.first())!!)
                    }
                }
            }
        }

        compose.onNodeWithText("First").performClick()
        compose.onNodeWithText("Absent, no charge").performClick()
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithText("Absent, charged").performClick()

        val session = env.classRepo.getSessionFor(ids.first())!!
        assertEquals(ids.toSet(), session.ids)
        assertEquals(0, session.present)
        assertEquals(Attended.ABSENT, session.classes.first().attended)
        assertEquals(Money.ZERO, session.classes.first().charge)
        assertEquals(Attended.ABSENT_BILLED, session.classes.last().attended)
        assertEquals(Money.ofMajor(30), session.classes.last().charge)
    }
}
