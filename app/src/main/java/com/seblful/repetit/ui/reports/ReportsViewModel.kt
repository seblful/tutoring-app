package com.seblful.repetit.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.PaymentRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.Billing
import com.seblful.repetit.domain.Reports
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.format.formatMonthKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/** A line in a report: a human label (month, student, or subject) and its money amount. */
data class LabeledAmount(val label: String, val amount: Money)

data class ReportsState(
    val totalIncome: Money = Money.ZERO,
    val byMonth: List<LabeledAmount> = emptyList(),
    /**
     * Income split into one-to-one and group teaching. Carries the kind rather than a label, because
     * the two words for it are string resources and belong on the screen — every other breakdown here
     * is labelled by a name that came out of the database.
     *
     * Empty unless **both** kinds earned something: a single line would only restate the total.
     */
    val byKind: List<Reports.KindIncome> = emptyList(),
    val bySubject: List<LabeledAmount> = emptyList(),
    val byStudent: List<LabeledAmount> = emptyList(),
    val debts: List<LabeledAmount> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
) {
    val isEmpty: Boolean get() = totalIncome.isZero && debts.isEmpty()
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    payments: PaymentRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<ReportsState> = combine(
        classes.observeAll(),
        students.observeAll(),
        subjects.observeAll(),
        payments.observeAll(),
    ) { classList, studentList, subjectList, paymentList ->
        // Reports cover active students only: an archived student is retired, so their classes,
        // income and debts should no longer appear in totals or breakdowns.
        val activeStudents = studentList.filter { !it.archived }
        val activeIds = activeStudents.map { it.id }.toSet()
        val activeClasses = classList.filter { it.studentId in activeIds }
        val activePayments = paymentList.filter { it.studentId in activeIds }

        val studentNames = activeStudents.associate { it.id to it.name }
        val subjectNames = subjectList.associate { it.id to it.name }
        val balances = Billing.balancesFor(activeStudents, activeClasses, activePayments)

        ReportsState(
            totalIncome = Reports.totalIncome(activeClasses),
            byMonth = Reports.incomeByMonth(activeClasses, zone)
                .map { LabeledAmount(formatMonthKey(it.monthKey), it.total) },
            // Only worth a section when there is a split to see; a tutor who teaches one way would
            // otherwise get a heading over one line repeating the total above it.
            byKind = Reports.incomeByKind(activeClasses).takeIf { it.size > 1 }.orEmpty(),
            bySubject = Reports.incomeBySubject(activeClasses)
                .toSortedLines { subjectNames[it] },
            byStudent = Reports.incomeByStudent(activeClasses)
                .toSortedLines { studentNames[it] },
            debts = Reports.debts(balances)
                .map { LabeledAmount(studentNames[it.studentId].orEmpty(), it.amount) },
            totalOutstanding = Reports.totalOutstanding(balances),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    /** Drops zero/unpriced entries and orders by amount, largest first. */
    private fun Map<Long, Money>.toSortedLines(nameOf: (Long) -> String?): List<LabeledAmount> =
        entries
            .filter { it.value.minor > 0 }
            .sortedByDescending { it.value.minor }
            .map { LabeledAmount(nameOf(it.key).orEmpty(), it.value) }
}
