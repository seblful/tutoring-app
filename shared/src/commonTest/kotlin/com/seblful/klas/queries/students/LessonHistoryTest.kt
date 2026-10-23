package com.seblful.klas.queries.students

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LessonHistoryTest {
    private fun row(id: Long, at: String, status: LessonStatus, minutes: Int = 60) = ClassRow(
        klass = TutoringClass(
            id = id,
            studentId = 1,
            subjectId = 1,
            start = Instant.parse(at),
            durationMinutes = minutes,
            price = Money.ofMajor(30),
            status = status,
            charge = if (status.isBillable) ClassRules.chargeFor(Money.ofMajor(30), minutes) else Money.ZERO,
        ),
        subjectName = "Physics",
    )

    private val planned = row(1, "2026-11-03T16:00:00Z", LessonStatus.SCHEDULED, minutes = 90)
    private val taught = row(2, "2026-10-13T16:00:00Z", LessonStatus.COMPLETED, minutes = 90)
    private val taughtToo = row(3, "2026-10-06T16:00:00Z", LessonStatus.COMPLETED)
    private val cancelled = row(4, "2026-10-02T16:00:00Z", LessonStatus.CANCELLED)
    private val rows = listOf(planned, taught, taughtToo, cancelled)

    @Test
    fun `lessons are cut into months in their order, each with what was held and earned`() {
        val months = LessonMonth.of(rows, TimeZone.UTC)

        assertEquals(listOf(YearMonth(2026, 11), YearMonth(2026, 10)), months.map { it.month })
        val october = months[1]
        assertEquals(listOf(2L, 3L, 4L), october.rows.map { it.klass.id })
        assertEquals(2, october.held)
        assertEquals(150, october.minutes)
        assertEquals(Money.ofMajor(75), october.earned)
        assertEquals(Money.ZERO, months[0].earned)
    }

    @Test
    fun `a planned lesson expects its booked rate for its length, and others expect nothing`() {
        assertEquals(Money.ofMajor(45), planned.expectedCharge)
        assertNull(taught.expectedCharge)
        assertNull(cancelled.expectedCharge)
    }

    @Test
    fun `the history counts each status and narrows to the one picked`() {
        val details = StudentDetails(classes = rows)

        assertEquals(
            mapOf(LessonStatus.SCHEDULED to 1, LessonStatus.COMPLETED to 2, LessonStatus.CANCELLED to 1),
            statusTally(rows),
        )
        val taughtOnly = details.lessonsShown(LessonFilter.ALL, LessonStatus.COMPLETED)
        assertEquals(listOf(2L, 3L), taughtOnly.map { it.klass.id })
        assertEquals(rows, details.lessonsShown(LessonFilter.ALL))
    }
}
