package com.seblful.klas.queries.students

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
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
    fun `lessons are cut into months in their order each with what was held and earned`() {
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
    fun `only a taught lesson shows an amount - planned and cancelled ones show none`() {
        assertEquals(null, planned.shownCharge)
        assertEquals(null, cancelled.shownCharge)
        assertEquals(true, taught.shownCharge != null)
    }

    private fun StudentDetails.shown(kind: LessonFilter, status: LessonStatus? = null) =
        history(kind, status, TimeZone.UTC).months.flatMap { it.rows }

    @Test
    fun `the history counts each status and narrows to the one picked`() {
        val details = StudentDetails(classes = rows)

        assertEquals(
            mapOf(LessonStatus.SCHEDULED to 1, LessonStatus.COMPLETED to 2, LessonStatus.CANCELLED to 1),
            details.history(LessonFilter.ALL, LessonStatus.COMPLETED, TimeZone.UTC).statuses,
            "every status keeps its count while one is picked",
        )
        assertEquals(listOf(2L, 3L), details.shown(LessonFilter.ALL, LessonStatus.COMPLETED).map { it.klass.id })
        assertEquals(rows, details.shown(LessonFilter.ALL))
    }

    @Test
    fun `the status chips count every kind while one kind is picked`() {
        val inGroup = row(5, "2026-10-20T16:00:00Z", LessonStatus.COMPLETED).let {
            it.copy(klass = it.klass.copy(groupId = 7))
        }
        val details = StudentDetails(
            classes = rows + inGroup,
            lessonKinds = mapOf(LessonKind.INDIVIDUAL to 4, LessonKind.GROUP to 1),
        )

        val group = details.history(LessonFilter.GROUP, null, TimeZone.UTC)

        assertEquals(listOf(inGroup), group.months.flatMap { it.rows })
        assertEquals(3, group.statuses[LessonStatus.COMPLETED])
        assertEquals(mapOf(LessonKind.INDIVIDUAL to 4, LessonKind.GROUP to 1), group.kinds)
    }

    @Test
    fun `a student taught one way gets no kind chips`() {
        val details = StudentDetails(classes = rows, lessonKinds = mapOf(LessonKind.INDIVIDUAL to 4))

        assertNull(details.history(LessonFilter.ALL, null, TimeZone.UTC).kinds)
    }
}
