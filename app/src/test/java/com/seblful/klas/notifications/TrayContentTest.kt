package com.seblful.klas.notifications

import com.seblful.klas.domain.DaySummary
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * How much of a day's agenda actually reaches the tray.
 *
 * This was arithmetic inside `AndroidNotifier` — 355 lines behind a `Context`, with no test in either
 * source set — even though [Notifier]'s own contract puts every other decision about a digest on the pure
 * side of the seam.
 */
class TrayContentTest {

    private val start = Instant.parse("2026-06-20T07:00:00Z")

    private fun agenda(lessons: Int) = Digests.Agenda(
        day = LocalDate.of(2026, 6, 20),
        summary = DaySummary(lessons = lessons, minutes = lessons * 60, money = Money.ZERO, cancelled = 0),
        lessons = (0 until lessons).map { i ->
            Digests.Agenda.Entry(
                start = start.plusSeconds(i * 3_600L),
                subject = "Алгебра",
                who = "Ученик $i",
            )
        },
    )

    @Test
    fun `a short day is named in full and counts nothing extra`() {
        val posting = TrayContent.agenda(agenda(3))

        assertEquals(3, posting.lines.size)
        assertEquals("nothing was dropped", 0, posting.more)
    }

    /** The boundary: exactly what fits is still "nothing left over", not "+0 more". */
    @Test
    fun `a day of exactly the cap is named in full`() {
        val posting = TrayContent.agenda(agenda(TrayContent.MAX_AGENDA_LINES))

        assertEquals(TrayContent.MAX_AGENDA_LINES, posting.lines.size)
        assertEquals(0, posting.more)
    }

    @Test
    fun `a fuller day is capped and says how many it did not name`() {
        val posting = TrayContent.agenda(agenda(TrayContent.MAX_AGENDA_LINES + 4))

        assertEquals(TrayContent.MAX_AGENDA_LINES, posting.lines.size)
        assertEquals(4, posting.more)
    }

    /** The lessons that survive are the first ones, so a capped agenda still starts at the morning. */
    @Test
    fun `the lessons it keeps are the earliest ones, in order`() {
        val posting = TrayContent.agenda(agenda(TrayContent.MAX_AGENDA_LINES + 2))

        assertEquals(start, posting.lines.first().start)
        assertEquals(
            "still in the order the digest assembled them",
            posting.lines.map { it.start }.sorted(),
            posting.lines.map { it.start },
        )
    }
}
