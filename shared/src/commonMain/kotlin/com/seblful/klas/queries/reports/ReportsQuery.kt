package com.seblful.klas.queries.reports

import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Observes a report's source rows and recomputes for period, date and zone changes. */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
class ReportsQuery @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val balances: BalanceRepository,
    private val clock: WallClock,
) {
    private val zone: TimeZone get() = clock.zone

    private data class Sources(
        val classes: List<TutoringClass>,
        val students: List<Student>,
        val subjects: List<Subject>,
        val groups: List<Group>,
        val balances: Map<Long, StudentBalance>,
    )

    fun observe(periods: Flow<ReportPeriod>): Flow<Reports.Report> = periods.distinctUntilChanged()
        .flatMapLatest { period ->
            combine(
                classesIn(Reports.window(period)),
                students.observeAll(),
                subjects.observeAll(),
                groups.observeAll(),
                balances.observe(),
                ::Sources,
            )
                // Today re-read at midnight ([days]), not once per emission: the current period's per-day
                // figures count the days gone, and the tab stays alive behind the bar overnight.
                .combine(clock.days()) { sources, today -> sources.report(period, today) }
        }

    /**
     * The classes a report needs, not every one taught. A day wider at each end, so the window still
     * covers its days after the zone changes; [Reports.of] applies the period itself.
     */
    private fun classesIn(window: ReportPeriod.Bounds?): Flow<List<TutoringClass>> = if (window == null) {
        classes.observeAll()
    } else {
        classes.observeBetween(
            window.from.minus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
            window.untilExclusive.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
    }

    private fun Sources.report(period: ReportPeriod, today: LocalDate): Reports.Report = Reports.of(
        period = period,
        classes = classes,
        students = students,
        balances = balances.values.toList(),
        // Every student with a class or payment has a balance row, billed or not.
        hasData = students.any { !it.archived && it.id in balances },
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
