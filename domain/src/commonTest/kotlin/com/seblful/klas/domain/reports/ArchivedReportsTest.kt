package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ArchivedReportsTest {
    @Test
    fun `archiving a student preserves the complete financial report`() {
        val student = Student(id = 1, name = "Former student")
        val start = Instant.parse("2026-05-01T10:00:00Z")
        val lesson = TutoringClass(
            id = 1, lessonId = 1, studentId = student.id, subjectId = 1,
            start = start, durationMinutes = 60, price = Money.ofMajor(30),
            status = LessonStatus.COMPLETED, attended = Attended.PRESENT, charge = Money.ofMajor(30),
        )
        fun report(who: Student) = reportFromHistory(
            period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 5)),
            classes = listOf(lesson),
            students = listOf(who),
            payments = listOf(Payment(studentId = who.id, date = start, amount = Money.ofMajor(10))),
            subjectNames = emptyMap(),
            groupNames = emptyMap(),
            zone = TimeZone.UTC,
            today = LocalDate(2026, 5, 20),
        )

        val before = report(student)
        val archived = report(student.copy(archived = true))

        assertEquals(before, archived)
        assertTrue(archived.hasData)
        assertEquals(Money.ofMajor(30), archived.totalIncome)
        assertEquals(Money.ofMajor(20), archived.totalOutstanding)
        assertEquals("Former student", archived.debts.single().label)
    }
}
