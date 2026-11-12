package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.TutoringClass

/**
 * Whom a report is narrowed to: everything, one student, or one subject. One at a time, as the schedule's
 * and homework's filters are — two narrowings at once answer a question nobody asks of a year.
 */
sealed interface ReportScope {
    data object Everything : ReportScope

    data class Student(val id: Long) : ReportScope

    data class Subject(val id: Long) : ReportScope

    /** Whether money received can be told apart under this scope. */
    val showsReceived: Boolean get() = this !is Subject
}

/** The lessons the report adds up under this scope. */
internal fun ReportScope.classes(all: List<TutoringClass>): List<TutoringClass> = when (this) {
    ReportScope.Everything -> all
    is ReportScope.Student -> all.filter { it.studentId == id }
    is ReportScope.Subject -> all.filter { it.subjectId == id }
}

/**
 * The payments under this scope. **None for a subject**: a payment is a student's, not a subject's, so
 * there is no honest share of it to show — the screen hides the received view instead.
 */
internal fun ReportScope.payments(all: List<Payment>): List<Payment> = when (this) {
    ReportScope.Everything -> all
    is ReportScope.Student -> all.filter { it.studentId == id }
    is ReportScope.Subject -> emptyList()
}

/** The debts under this scope: one student's own, or everyone's, which a subject cannot divide. */
internal fun ReportScope.balances(all: List<StudentBalance>): List<StudentBalance> = when (this) {
    is ReportScope.Student -> all.filter { it.studentId == id }
    else -> all
}
