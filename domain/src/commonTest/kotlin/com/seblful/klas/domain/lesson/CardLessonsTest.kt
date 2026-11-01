package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CardLessonsTest {

    private val now = Instant.parse("2026-06-15T16:30:00Z")

    private fun lesson(id: Long, start: String, status: LessonStatus = LessonStatus.SCHEDULED) = TutoringClass(
        id = id,
        studentId = 1,
        subjectId = 1,
        start = Instant.parse(start),
        durationMinutes = 60,
        status = status,
    )

    private fun next(vararg rows: TutoringClass) = CardLessons.next(rows.toList(), now) { it }?.id

    private fun preview(vararg rows: TutoringClass) =
        CardLessons.preview(rows.toList(), now, limit = 2) { it }.map { it.id }

    @Test
    fun `one under way is next ahead of a later booking`() {
        val rows = arrayOf(
            lesson(1, "2026-06-14T16:00:00Z"),
            lesson(3, "2026-06-16T16:00:00Z"),
            lesson(2, "2026-06-15T16:00:00Z"),
        )
        assertEquals(2L, next(*rows))
    }

    @Test
    fun `a cancelled lesson is never next and with none left there is none`() {
        assertNull(next(lesson(1, "2026-06-16T16:00:00Z", LessonStatus.CANCELLED), lesson(2, "2026-06-14T16:00:00Z")))
    }

    @Test
    fun `the preview is the latest begun newest first without the next`() {
        val rows = arrayOf(
            lesson(1, "2026-06-10T16:00:00Z"),
            lesson(2, "2026-06-12T16:00:00Z"),
            lesson(3, "2026-06-14T16:00:00Z"),
            lesson(4, "2026-06-15T16:00:00Z"),
        )
        assertEquals(listOf(3L, 2L), preview(*rows))
    }

    @Test
    fun `with nothing begun the preview is the bookings after the next soonest first`() {
        val rows = arrayOf(
            lesson(3, "2026-06-20T16:00:00Z"),
            lesson(1, "2026-06-16T16:00:00Z"),
            lesson(2, "2026-06-18T16:00:00Z"),
        )
        assertEquals(listOf(2L, 3L), preview(*rows))
    }
}
