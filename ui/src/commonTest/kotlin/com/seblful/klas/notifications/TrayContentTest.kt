package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.schedule.DaySummary
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TrayContentTest {

    private val start = Instant.parse("2026-06-20T07:00:00Z")

    private fun agenda(lessons: Int) = Digests.Agenda(
        day = LocalDate(2026, 6, 20),
        summary = DaySummary(lessons = lessons, minutes = lessons * 60, money = Money.ZERO, cancelled = 0),
        lessons = (0 until lessons).map { i ->
            Digests.Agenda.Entry(
                start = start.plus((i * 3_600L).seconds),
                subject = "Алгебра",
                who = "Ученик $i",
            )
        },
    )

    @Test
    fun `a short day is named in full and counts nothing extra`() {
        val posting = TrayContent.agenda(agenda(3))

        assertEquals(3, posting.lines.size)
        assertEquals(0, posting.more, "nothing was dropped")
    }

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

    @Test
    fun `the lessons it keeps are the earliest ones in order`() {
        val posting = TrayContent.agenda(agenda(TrayContent.MAX_AGENDA_LINES + 2))

        assertEquals(start, posting.lines.first().start)
        assertEquals(
            posting.lines.map { it.start }.sorted(),
            posting.lines.map { it.start },
            "still in the order the digest assembled them",
        )
    }
}
