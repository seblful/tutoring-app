package com.seblful.klas.ui.people

import androidx.compose.ui.test.junit4.createComposeRule
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.common.timeText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LessonLineTextTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `the next lesson is said by day within a week, by date past it, and the last one by date`() {
        val four = LocalTime(16, 0)
        val lines = listOf(
            LessonLine.Next(LocalDate(2026, 6, 1), four, daysAway = 0, thisYear = 2026),
            LessonLine.Next(LocalDate(2026, 6, 2), four, daysAway = 1, thisYear = 2026),
            LessonLine.Next(LocalDate(2026, 6, 4), four, daysAway = 3, thisYear = 2026),
            LessonLine.Next(LocalDate(2026, 6, 24), four, daysAway = 23, thisYear = 2026),
            LessonLine.Next(LocalDate(2027, 1, 12), four, daysAway = 225, thisYear = 2026),
            LessonLine.Last(LocalDate(2026, 5, 20), thisYear = 2026),
            LessonLine.Last(LocalDate(2025, 12, 20), thisYear = 2026),
            LessonLine.Never,
        )
        var said = emptyList<String>()
        var time = ""
        compose.setContent {
            time = four.timeText()
            said = lines.map { lessonLineText(it) }
        }
        compose.waitForIdle()

        assertEquals(
            listOf(
                "Today $time", "Tomorrow $time", "Thu $time", "Jun 24", "Jan 12, 2027",
                "Last lesson May 20", "Last lesson Dec 20, 2025", "No lessons yet",
            ),
            said,
        )
    }
}
