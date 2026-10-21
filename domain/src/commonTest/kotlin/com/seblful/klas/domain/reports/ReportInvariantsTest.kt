package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ReportInvariantsTest {
    @Test
    fun `generated reports conserve income across breakdowns and input order`() {
        val students = (1L..5L).map { Student(id = it, name = "Student $it", archived = it % 2L == 0L) }
        val subjects = (1L..3L).associate { SubjectId(it) to "Subject $it" }
        for (seed in 0..39) {
            val random = Random(seed)
            val classes = (1L..40L).map { id ->
                val completed = id % 3L != 0L
                TutoringClass(
                    id = id, studentId = random.nextLong(1, 6), subjectId = random.nextLong(1, 4),
                    start = Instant.parse("2025-01-01T10:00:00Z") + random.nextInt(730).days,
                    durationMinutes = 60, groupId = if (id % 2L == 0L) 1 else null,
                    status = if (completed) LessonStatus.COMPLETED else LessonStatus.CANCELLED,
                    attended = if (completed) Attended.PRESENT else Attended.EXPECTED,
                    charge = if (completed) Money(random.nextLong(1, 100_001)) else Money.ZERO,
                )
            }
            for (kind in ReportPeriod.Kind.entries) {
                val period = ReportPeriod(kind, YearMonth(2026, 6))
                fun report(rows: List<TutoringClass>) = Reports.of(
                    period, rows, students, emptyList(), subjects, mapOf(GroupId(1) to "Group"),
                    TimeZone.UTC, LocalDate(2026, 6, 15),
                )
                val report = report(classes)
                val context = "seed=$seed period=$kind"
                val bounds = period.bounds
                val expectedIncome = classes.filter {
                    val day = it.start.toLocalDateTime(TimeZone.UTC).date
                    it.status == LessonStatus.COMPLETED &&
                        (bounds == null || (day >= bounds.from && day < bounds.untilExclusive))
                }.sumOf { it.charge.minor }
                assertEquals(Money(expectedIncome), report.totalIncome, context)
                assertEquals(report.totalIncome.minor, report.byStudent.sumOf { it.amount.minor }, context)
                assertEquals(report.totalIncome.minor, report.bySubject.sumOf { it.amount.minor }, context)
                assertEquals(report.totalIncome.minor, report.trend.bars.sumOf { it.total.minor }, context)
                if (report.byKind.isNotEmpty()) {
                    assertEquals(report.totalIncome.minor, report.byKind.sumOf { it.amount.minor }, context)
                }
                assertEquals(report, report(classes.shuffled(random)), context)
            }
        }
    }
}
