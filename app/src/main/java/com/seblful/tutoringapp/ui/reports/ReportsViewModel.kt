package com.seblful.tutoringapp.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.PaymentRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.Billing
import com.seblful.tutoringapp.domain.Reports
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.format.formatMonthKey
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
        val studentNames = studentList.associate { it.id to it.name }
        val subjectNames = subjectList.associate { it.id to it.name }
        val balances = Billing.balancesFor(studentList, classList, paymentList)

        ReportsState(
            totalIncome = Reports.totalIncome(classList),
            byMonth = Reports.incomeByMonth(classList, zone)
                .map { LabeledAmount(formatMonthKey(it.monthKey), it.total) },
            bySubject = Reports.incomeBySubject(classList)
                .toSortedLines { subjectNames[it] },
            byStudent = Reports.incomeByStudent(classList)
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
