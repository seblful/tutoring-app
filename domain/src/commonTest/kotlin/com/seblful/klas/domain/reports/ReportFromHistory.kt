package com.seblful.klas.domain.reports

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** [Reports.Sources] from every class and payment there is, the balances summed as the database sums them. */
internal fun historySources(
    classes: List<TutoringClass>,
    students: List<Student>,
    payments: List<Payment> = emptyList(),
    subjectNames: Map<SubjectId, String> = emptyMap(),
    groupNames: Map<GroupId, String> = emptyMap(),
): Reports.Sources {
    // The database has a balance row for each student with a class or payment, and none for the rest.
    val touched = (classes.map { it.studentId } + payments.map { it.studentId }).toSet()
    val balances = Billing.balancesFor(students.filter { it.id in touched }, classes, payments)
    return Reports.Sources(classes, students, balances.associateBy { it.studentId }, payments, subjectNames, groupNames)
}

/** [Reports.of] over [historySources]. */
internal fun reportFromHistory(
    period: ReportPeriod,
    classes: List<TutoringClass>,
    students: List<Student>,
    payments: List<Payment>,
    subjectNames: Map<SubjectId, String>,
    groupNames: Map<GroupId, String>,
    zone: TimeZone,
    today: LocalDate,
    scope: ReportScope = ReportScope.Everything,
): Reports.Report =
    Reports.of(period, historySources(classes, students, payments, subjectNames, groupNames), zone, today, scope)
