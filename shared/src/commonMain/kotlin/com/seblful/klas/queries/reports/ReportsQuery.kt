package com.seblful.klas.queries.reports

import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.days
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Observes a report's source rows and recomputes for period, date and zone changes. */
@SingleIn(AppScope::class)
class ReportsQuery @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val payments: PaymentRepository,
    private val clock: WallClock,
) {
    private val zone: TimeZone get() = clock.zone

    private data class Sources(
        val classes: List<TutoringClass>,
        val students: List<Student>,
        val subjects: List<Subject>,
        val groups: List<Group>,
        val payments: List<Payment>,
    )

    fun observe(periods: Flow<ReportPeriod>): Flow<Reports.Report> = combine(
        classes.observeAll(),
        students.observeAll(),
        subjects.observeAll(),
        groups.observeAll(),
        payments.observeAll(),
        ::Sources,
    ).combine(periods, ::Pair)
        // Today re-read at midnight ([days]), not once per emission: the current period's per-day
        // figures count the days gone, and the tab stays alive behind the bar overnight.
        .combine(clock.days()) { (sources, period), today -> sources.report(period, today) }

    private fun Sources.report(period: ReportPeriod, today: LocalDate): Reports.Report = Reports.of(
        period = period,
        classes = classes,
        students = students,
        payments = payments,
        // Keyed by typed ids, so each table can only be handed to the breakdown it names. The two are
        // otherwise interchangeable, and a mismatch drew a real amount against a blank label rather
        // than failing — see `model/Ids.kt`.
        subjectNames = subjects.associate { SubjectId(it.id) to it.name },
        // Archived groups included: income a group already earned stays the tutor's, and a line
        // labelled by an id would be the only unreadable row on the screen.
        groupNames = groups.associate { GroupId(it.id) to it.name },
        zone = zone,
        today = today,
    )
}
