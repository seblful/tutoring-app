package com.seblful.klas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.ReportPeriod
import com.seblful.klas.domain.Reports
import com.seblful.klas.domain.Taught
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * A line in a report: a human label (a student, a subject, a group) and its money amount.
 *
 * @param percent the share of the period's income this line is, or **null** where a share would be a
 *   lie: the lines of a section that does not partition the income (groups) or that measures something
 *   else entirely (debts) are not parts of one whole, so no percentage of it is theirs to print.
 */
data class LabeledAmount(val label: String, val amount: Money, val percent: Int? = null)

/**
 * One kind of teaching's income and share. Carries the kind rather than a label, because the two
 * words for it are string resources and belong on the screen — every other breakdown here is
 * labelled by a name that came out of the database.
 */
data class KindAmount(val kind: LessonKind, val amount: Money, val percent: Int)

data class ReportsState(
    /** The slice of time every income number below covers. Debts are the documented exception. */
    val period: ReportPeriod,
    /**
     * Whether the database holds any lesson or payment at all — false only before the first one is
     * booked. Kept apart from "this period earned nothing", so an empty year still shows the picker
     * that gets the tutor out of it rather than a dead end saying there is no data.
     */
    val hasData: Boolean = false,
    val totalIncome: Money = Money.ZERO,
    /** How much teaching the period holds — a group lesson counted once. See [Taught.overall]. */
    val taught: Taught.Totals = Taught.Totals.NONE,
    val trend: Reports.Trend = Reports.Trend.NONE,
    /** Empty unless **both** kinds earned something: a single line would only restate the total. */
    val byKind: List<KindAmount> = emptyList(),
    val byGroup: List<LabeledAmount> = emptyList(),
    val bySubject: List<LabeledAmount> = emptyList(),
    val byStudent: List<LabeledAmount> = emptyList(),
    val debts: List<LabeledAmount> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
) {
    /** The period earned nothing — the sections below the total have nothing to break down. */
    val noIncome: Boolean get() = totalIncome.isZero
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    payments: PaymentRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _period = MutableStateFlow(ReportPeriod.currentYear(LocalDate.now(zone)))
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    /** Everything a report is assembled from — five flows, so the period can be a sixth. */
    private data class Sources(
        val classes: List<TutoringClass>,
        val students: List<Student>,
        val subjects: List<Subject>,
        val groups: List<Group>,
        val payments: List<Payment>,
    )

    val state: StateFlow<ReportsState> = combine(
        classes.observeAll(),
        students.observeAll(),
        subjects.observeAll(),
        groups.observeAll(),
        payments.observeAll(),
        ::Sources,
    ).combine(_period) { sources, period -> sources.report(period) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState(_period.value))

    /** Reads the same period at another width, keeping where the tutor was looking. */
    fun setKind(kind: ReportPeriod.Kind) = _period.update { it.withKind(kind) }

    /** Steps one period back (−1) or forward (+1). All time ignores it — it has nowhere to go. */
    fun shift(by: Int) = _period.update { it.shifted(by) }

    private fun Sources.report(period: ReportPeriod): ReportsState {
        // Reports cover active students only: an archived student is retired, so their classes,
        // income and debts should no longer appear in totals or breakdowns.
        val activeStudents = students.filter { !it.archived }
        val activeIds = activeStudents.map { it.id }.toSet()
        val activeClasses = classes.filter { it.studentId in activeIds }
        val activePayments = payments.filter { it.studentId in activeIds }

        // Keyed by typed ids, so each table can only be handed to the breakdown it names. The three
        // are otherwise interchangeable, and a mismatch drew a real amount against a blank label
        // rather than failing — see `model/Ids.kt`.
        val studentNames = activeStudents.associate { StudentId(it.id) to it.name }
        val subjectNames = subjects.associate { SubjectId(it.id) to it.name }
        // Archived groups included: income a group already earned stays the tutor's, and a line
        // labelled by an id would be the only unreadable row on the screen.
        val groupNames = groups.associate { GroupId(it.id) to it.name }

        // A balance spans a student's whole history, so it is built from every class and payment —
        // clipping the charges to a period while the payments stay whole would invent credit.
        val balances = Billing.balancesFor(activeStudents, activeClasses, activePayments)
        val inPeriod = period.filter(activeClasses, zone)
        val total = Reports.totalIncome(inPeriod)
        val outstanding = Reports.totalOutstanding(balances)

        return ReportsState(
            period = period,
            hasData = activeClasses.isNotEmpty() || activePayments.isNotEmpty(),
            totalIncome = total,
            taught = Taught.overall(inPeriod),
            trend = Reports.trend(activeClasses, period, zone),
            // Only worth a section when there is a split to see; a tutor who teaches one way would
            // otherwise get a heading over one line repeating the total above it.
            byKind = Reports.incomeByKind(inPeriod)
                .takeIf { it.size > 1 }
                .orEmpty()
                .map { KindAmount(it.kind, it.total, Reports.percentOf(it.total, total)) },
            // No share: a group's lessons are only part of the income, so «38 %» here would read as a
            // share of everything while summing to the group half — the kind split above says that.
            byGroup = Reports.incomeByGroup(inPeriod).toSortedLines(whole = null) { groupNames[it] },
            bySubject = Reports.incomeBySubject(inPeriod).toSortedLines(total) { subjectNames[it] },
            byStudent = Reports.incomeByStudent(inPeriod).toSortedLines(total) { studentNames[it] },
            // No share either: what matters about a debt is the money and who owes it, and a
            // percentage of the arrears is not something to act on.
            debts = Reports.debts(balances).map {
                LabeledAmount(label = studentNames[it.student].orEmpty(), amount = it.amount)
            },
            totalOutstanding = outstanding,
        )
    }

    /**
     * Drops zero/unpriced entries and orders by amount, largest first.
     *
     * @param whole what these lines are parts of, or null when they are not parts of anything — see
     *   [LabeledAmount.percent].
     */
    private fun <K> Map<K, Money>.toSortedLines(
        whole: Money?,
        nameOf: (K) -> String?,
    ): List<LabeledAmount> =
        entries
            .filter { it.value.minor > 0 }
            .sortedByDescending { it.value.minor }
            .map {
                LabeledAmount(
                    label = nameOf(it.key).orEmpty(),
                    amount = it.value,
                    percent = whole?.let { w -> Reports.percentOf(it.value, w) },
                )
            }
}
