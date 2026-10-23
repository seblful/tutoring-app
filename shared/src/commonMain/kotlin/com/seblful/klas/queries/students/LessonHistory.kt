package com.seblful.klas.queries.students

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/**
 * One month of a student's lessons, as their lesson history groups them: the rows in the order given, and
 * what the month came to.
 */
data class LessonMonth(val month: YearMonth, val rows: List<ClassRow>) {
    /** Lessons that took or take the tutor's hour — all but the cancelled. */
    val held: Int get() = rows.count { it.klass.status.occupiesTime }

    val minutes: Int get() = rows.filter { it.klass.status.occupiesTime }.sumOf { it.klass.durationMinutes }

    /** What the month earned — taught lessons only; a planned lesson's amount is a forecast, not income. */
    val earned: Money get() = Billing.charged(rows.map { it.klass })

    companion object {
        /** [rows] cut into months in [zone], keeping their order — newest first, as the history lists them. */
        fun of(rows: List<ClassRow>, zone: TimeZone): List<LessonMonth> =
            rows.groupBy { it.klass.start.toLocalDateTime(zone).date.yearMonth }
                .map { (month, inMonth) -> LessonMonth(month, inMonth) }
    }
}

/** How many lessons have each status, for the history's chips. A status with none is absent. */
fun statusTally(rows: List<ClassRow>): Map<LessonStatus, Int> = rows.groupingBy { it.klass.status }.eachCount()
