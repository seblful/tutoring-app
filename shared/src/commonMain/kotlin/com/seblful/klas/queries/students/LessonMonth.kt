package com.seblful.klas.queries.students

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.LessonKind
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

/** A student's lesson history as narrowed: its months, and the chips that narrow it. */
data class LessonHistory(
    val months: List<LessonMonth>,
    /**
     * How many lessons have each status, over every lesson whatever the kind picked — so the chips keep their
     * numbers while the kind changes. A status with none is absent.
     */
    val statuses: Map<LessonStatus, Int>,
    /** How many lessons of each kind; null when the student is taught only one way, so there is nothing to pick. */
    val kinds: Map<LessonKind, Int>?,
)
