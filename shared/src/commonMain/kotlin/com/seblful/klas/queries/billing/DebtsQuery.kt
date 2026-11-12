package com.seblful.klas.queries.billing

import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.reports.Reports
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant

/** One student who owes: who, how much over their whole history, and when they last paid (null for never). */
data class Debtor(val studentId: Long, val name: String, val owed: Money, val lastPaidAt: Instant?)

/**
 * Who owes the tutor what. A balance spans a student's whole history, never a period: charges from this
 * year against payments from every year would invent credit.
 */
class Debts internal constructor(
    private val sums: Map<Long, StudentBalance>,
    active: List<Student>,
    payments: List<Payment>,
) {
    /**
     * How many **active** students owe, and what in all. An overpaid student does not reduce what another
     * owes, and an archived one's debt is the reports' to show, not a reminder's.
     */
    val outstanding: Reports.Outstanding = Reports.Outstanding.of(active.map { of(it.id) })

    /** [outstanding]'s debtors, most owed first. */
    val debtors: List<Debtor> = run {
        val names = active.associate { it.id to it.name }
        val lastPaid = payments.groupBy { it.studentId }.mapValues { (_, theirs) -> theirs.maxOf { it.date } }
        outstanding.debts.mapNotNull { debt ->
            val id = debt.student.value
            names[id]?.let { Debtor(id, it, debt.amount, lastPaid[id]) }
        }
    }

    /** [studentId]'s balance, archived or not — zero for a student with no class or payment yet. */
    fun of(studentId: Long): StudentBalance = sums[studentId] ?: StudentBalance(studentId, Money.ZERO, Money.ZERO)
}

/** [Debts] as they stand, from SQLite's sums — no class history is read. */
class DebtsQuery @Inject constructor(
    private val balances: BalanceRepository,
    private val students: StudentRepository,
    private val payments: PaymentRepository,
) {
    fun observe(): Flow<Debts> =
        combine(balances.observe(), students.observeActive(), payments.observeBetween(null, null), ::Debts)

    suspend fun get(): Debts = Debts(balances.get(), students.getActive(), payments.getAll())
}
