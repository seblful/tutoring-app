package com.seblful.klas.ui.schedule.booking

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookingTimelineTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate(2026, 10, 22)

    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(TimeZone.UTC)

    // 9:00–23:00 with Мария 15:00–18:00: the bar from three to five fourteenths.
    private val maria =
        DayTimeline.Lesson(Scheduling.Slot(at(15), 180), "Мария", Assignee.Student(1), ClassMode.REMOTE)
    private val timeline = DayTimeline.of(day, TimeZone.UTC, listOf(maria))

    private var placed: Instant? = null

    private fun show(
        chosen: Scheduling.Slot? = null,
        suggestion: Scheduling.Slot? = null,
        mine: Assignee? = null,
        lessons: DayTimeline = timeline,
    ) {
        compose.setContent {
            KlasTheme {
                BookingTimeline(
                    timeline = lessons,
                    chosen = chosen,
                    length = 60,
                    clashes = false,
                    onStart = { placed = it },
                    suggestion = suggestion,
                    mine = mine,
                )
            }
        }
    }

    @Test
    fun `a tap on free time is read as the time under it`() {
        show()

        // 12:00 is three fourteenths of the way along 9:00–23:00.
        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 3f / 14, centerY)) }
        compose.waitForIdle()

        val tapped = placed!!
        assertTrue("tapped $tapped", (tapped - at(12)).absoluteValue.inWholeMinutes <= 5)
    }

    @Test
    fun `a tap on a lesson says who, when and where instead of booking`() {
        show()

        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.6f, centerY)) }
        compose.waitForIdle()

        compose.onNodeWithTag(PEEK_TAG).assertIsDisplayed()
        compose.onNodeWithText("Мария", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Remote", substring = true).assertIsDisplayed()
        assertNull("a lesson is not booked over", placed)
    }

    @Test
    fun `a tap on the offered frame books its start`() {
        show(suggestion = Scheduling.Slot(at(10), 60))

        compose.onNodeWithTag(SUGGESTION_TAG).performClick()
        compose.waitForIdle()

        assertEquals(at(10), placed)
    }

    @Test
    fun `a slide moves the lesson half as far as the finger, in five-minute steps`() {
        show(chosen = Scheduling.Slot(at(12), 60))

        // A tenth of the bar — 84 minutes of it — past the touch slop, from wherever the finger lands: the
        // lesson goes 42 minutes later than 12:00, whose nearest five-minute step is 12:40.
        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput {
            val from = width * 0.05f
            down(Offset(from, centerY))
            moveTo(Offset(from + viewConfiguration.touchSlop + width * 0.05f, centerY))
            moveTo(Offset(from + viewConfiguration.touchSlop + width * 0.1f, centerY))
        }
        compose.waitForIdle()
        assertNull("nothing is booked while the finger is down", placed)
        compose.onAllNodesWithTag(EDGE_TAG).assertCountEquals(2)

        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput { up() }
        compose.waitForIdle()

        assertEquals(at(12, 40), placed)
    }

    @Test
    fun `a slide past a lesson hops over it`() {
        show(chosen = Scheduling.Slot(at(13), 60))

        // From wherever the finger lands, 220 minutes on from 13:00 is 16:40 — inside Мария's 15:00–18:00
        // and nearer her end, so the hour goes after her.
        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput {
            val from = width * 0.05f
            down(Offset(from, centerY))
            listOf(0.2f, 0.4f, 440f / 840).forEach { travel ->
                moveTo(Offset(from + viewConfiguration.touchSlop + width * travel, centerY))
            }
            up()
        }
        compose.waitForIdle()

        assertEquals(at(18), placed)
    }

    @Test
    fun `the buttons under a chosen lesson move it five minutes at a time`() {
        show(chosen = Scheduling.Slot(at(13), 60))

        compose.onNodeWithText("5 minutes later").performClick()
        assertEquals(at(13, 5), placed)

        compose.onNodeWithText("5 minutes earlier").performClick()
        assertEquals(at(12, 55), placed)
    }

    @Test
    fun `the buttons hop a lesson over the next one in the way`() {
        // 14:00 for an hour already ends as Мария starts at 15:00; five minutes later puts it after her.
        show(chosen = Scheduling.Slot(at(14), 60))

        compose.onNodeWithText("5 minutes later").performClick()

        assertEquals(at(18), placed)
    }

    @Test
    fun `without a chosen lesson there is nothing to move`() {
        show(suggestion = Scheduling.Slot(at(10), 60))

        compose.onNodeWithText("5 minutes later").assertDoesNotExist()
    }

    @Test
    fun `a tap on the chosen lesson leaves it where it is`() {
        // 11:00–14:00, and the finger three tenths along 9:00–23:00, at 13:12.
        show(chosen = Scheduling.Slot(at(11), 180))

        compose.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.3f, centerY)) }
        compose.waitForIdle()

        assertNull(placed)
    }

    @Test
    fun `only the lessons of whoever this one is for are outlined`() {
        val ivan = DayTimeline.Lesson(Scheduling.Slot(at(10), 60), "Иван", Assignee.Student(2), ClassMode.IN_PERSON)
        show(mine = Assignee.Student(1), lessons = DayTimeline.of(day, TimeZone.UTC, listOf(maria, ivan)))

        compose.onAllNodesWithTag(MINE_TAG).assertCountEquals(1)
    }

    @Test
    fun `before anyone is chosen no lesson is outlined`() {
        show()

        compose.onAllNodesWithTag(MINE_TAG).assertCountEquals(0)
    }

    @Test
    fun `a second tap on the same lesson hides its bubble`() {
        show()

        repeat(2) {
            compose.onNodeWithTag(TIMELINE_TAG).performTouchInput { click(Offset(width * 0.6f, centerY)) }
            compose.waitForIdle()
        }

        compose.onNodeWithTag(PEEK_TAG).assertDoesNotExist()
        assertNull(placed)
    }
}
