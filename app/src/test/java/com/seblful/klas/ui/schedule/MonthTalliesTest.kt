package com.seblful.klas.ui.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** A day's tallies belong to the number above them, so they sit nearer it than the next week's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthTalliesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a day's tallies sit nearer its own number than the next week's`() {
        val day = LocalDate(2026, 10, 14)
        val fortyTwoPlanned = CalendarLayout.DayLoad(mapOf(LessonStatus.SCHEDULED to 42))
        compose.setContent {
            MaterialTheme {
                Surface {
                    MonthCalendar(
                        grid = CalendarLayout.monthGrid(YearMonth(2026, 10)),
                        loads = Loadable.Ready(mapOf(day to fortyTwoPlanned)),
                        selected = day,
                        today = day,
                        onSelect = {},
                    )
                }
            }
        }
        val own = bounds("14")
        val tallies = bounds("42")
        val next = bounds("21")
        val above = tallies.top - own.bottom
        val below = next.top - tallies.bottom
        assertTrue("${above}px to its own number, ${below}px to the next week's", above < below)
    }

    private fun bounds(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
