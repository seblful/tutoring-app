package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.StudentBalance
import com.seblful.repetit.domain.model.TutoringClass
import java.time.ZoneId

/**
 * Read-only aggregations for the Reports screen. Income counts only the charges captured on
 * completed classes (so it matches what was actually billed); cancelled/missed/scheduled
 * classes contribute nothing.
 */
object Reports {

    /** Income for one calendar month, keyed as `year * 12 + (monthValue - 1)` (newest first). */
    data class MonthlyIncome(val monthKey: Int, val total: Money)

    /** A student's outstanding debt (only students who actually owe appear). */
    data class Debt(val studentId: Long, val amount: Money)

    /** Income from one kind of teaching — see [incomeByKind]. */
    data class KindIncome(val kind: LessonKind, val total: Money)

    fun totalIncome(classes: List<TutoringClass>): Money =
        Billing.charged(classes)

    /** Completed-class income grouped by the month the class took place in [zone], newest first. */
    fun incomeByMonth(classes: List<TutoringClass>, zone: ZoneId): List<MonthlyIncome> =
        classes.filter { it.status.isBillable }
            .groupBy { monthKeyOf(it, zone) }
            .map { (key, list) -> MonthlyIncome(key, list.fold(Money.ZERO) { a, c -> a + c.charge }) }
            .sortedByDescending { it.monthKey }

    fun incomeByStudent(classes: List<TutoringClass>): Map<Long, Money> =
        sumChargesBy(classes) { it.studentId }

    fun incomeBySubject(classes: List<TutoringClass>): Map<Long, Money> =
        sumChargesBy(classes) { it.subjectId }

    /**
     * Completed-class income split into one-to-one and group teaching, largest first.
     *
     * This is the question the other breakdowns cannot answer: a group is usually taught below the
     * one-to-one rate, so "how much of my income comes from groups" decides whether the arrangement
     * is worth the hours — and per-student or per-subject lines both mix the two kinds inside every
     * row. A kind that earned nothing is **left out** rather than printed as a zero, which also means
     * a tutor who teaches only one way sees a single line saying no more than the total already does;
     * the screen drops the section then (see `ReportsViewModel`).
     */
    fun incomeByKind(classes: List<TutoringClass>): List<KindIncome> =
        classes.filter { it.status.isBillable }
            .groupBy { LessonKind.of(it) }
            .map { (kind, list) -> KindIncome(kind, list.fold(Money.ZERO) { a, c -> a + c.charge }) }
            .filterNot { it.total.isZero }
            .sortedByDescending { it.total.minor }

    /** Students with a positive balance (owe money), largest debt first. */
    fun debts(balances: List<StudentBalance>): List<Debt> =
        balances.filter { it.balance.minor > 0 }
            .map { Debt(it.studentId, it.balance) }
            .sortedByDescending { it.amount.minor }

    /** Sum of every positive balance — total money owed to the tutor (credits excluded). */
    fun totalOutstanding(balances: List<StudentBalance>): Money =
        balances.fold(Money.ZERO) { acc, b -> if (b.balance.minor > 0) acc + b.balance else acc }

    private inline fun sumChargesBy(classes: List<TutoringClass>, key: (TutoringClass) -> Long): Map<Long, Money> =
        classes.filter { it.status.isBillable }
            .groupBy(key)
            .mapValues { (_, list) -> list.fold(Money.ZERO) { a, c -> a + c.charge } }

    private fun monthKeyOf(c: TutoringClass, zone: ZoneId): Int {
        val date = c.start.atZone(zone).toLocalDate()
        return date.year * 12 + (date.monthValue - 1)
    }
}
