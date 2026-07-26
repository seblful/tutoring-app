package com.seblful.klas.domain

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.TutoringClass
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only aggregations for the Reports screen. Income counts only the charges captured on
 * completed classes (so it matches what was actually billed); cancelled/missed/scheduled
 * classes contribute nothing.
 *
 * Every function here takes the classes it should count, so *which* ones is one decision made once —
 * a [ReportPeriod] and the active-students rule, both applied in `ReportsViewModel` — rather than a
 * filter each aggregation could disagree about. [trend] is the exception and says why.
 */
object Reports {

    /** Income for one calendar month, keyed as `year * 12 + (monthValue - 1)` (newest first). */
    data class MonthlyIncome(val monthKey: Int, val total: Money)

    /** A student's outstanding debt (only students who actually owe appear). */
    data class Debt(val studentId: Long, val amount: Money)

    /** Income from one kind of teaching — see [incomeByKind]. */
    data class KindIncome(val kind: LessonKind, val total: Money)

    /**
     * The income trend a report is drawn with: one bar per bucket of time, **oldest first**.
     *
     * This is the one breakdown ordered by time rather than by size, which is exactly why it is a
     * chart and the others are ranked bars — a year's shape (a quiet January, a busy May) is a fact
     * about the order the months come in, and sorting them by amount destroys it.
     *
     * The buckets are one step finer than the period: a year is drawn as its months, all time as its
     * years. A single month therefore produces one bar and reports itself [isEmpty] — there is
     * nothing to compare it against that the total above it does not already say.
     */
    data class Trend(val bucket: Bucket, val bars: List<Bar>) {

        /** What one bar covers. */
        enum class Bucket { MONTHS, YEARS }

        /**
         * @param key a month key (`year * 12 + monthIndex`) when the bucket is [Bucket.MONTHS], a
         *   plain year when it is [Bucket.YEARS].
         */
        data class Bar(val key: Int, val total: Money)

        /** The best bucket — what every bar's height is a fraction of, and the one worth naming. */
        val peak: Bar? get() = bars.maxByOrNull { it.total.minor }?.takeUnless { it.total.isZero }

        /** Nothing a chart could show: no income at all, or a single bucket to compare with itself. */
        val isEmpty: Boolean get() = bars.size < 2 || peak == null

        companion object {
            val NONE = Trend(Bucket.MONTHS, emptyList())
        }
    }

    fun totalIncome(classes: List<TutoringClass>): Money =
        Billing.charged(classes)

    /** Completed-class income grouped by the month the class took place in [zone], newest first. */
    fun incomeByMonth(classes: List<TutoringClass>, zone: ZoneId): List<MonthlyIncome> =
        classes.filter { it.status.isBillable }
            .groupBy { monthKeyOf(it, zone) }
            .map { (key, list) -> MonthlyIncome(key, list.fold(Money.ZERO) { a, c -> a + c.charge }) }
            .sortedByDescending { it.monthKey }

    /**
     * The trend for [period], with **every** bucket it spans present — including the ones that
     * earned nothing, so a month with no lessons reads as a gap in the year rather than being closed
     * over by its neighbours. A month key with no income is a real answer; leaving it out would make
     * a three-month break look like three busy months in a row.
     *
     * Filters by the period itself rather than trusting the caller to have done it, so the bars can
     * never disagree with the total they are drawn under.
     */
    fun trend(classes: List<TutoringClass>, period: ReportPeriod, zone: ZoneId): Trend {
        val inPeriod = period.filter(classes, zone)
        return when (period.kind) {
            // All time has no bound to enumerate, so the data sets it: first earning year to last.
            ReportPeriod.Kind.ALL -> {
                val byYear = sumChargesBy(inPeriod) { it.start.atZone(zone).toLocalDate().year.toLong() }
                val years = byYear.keys.map { it.toInt() }
                Trend(
                    bucket = Trend.Bucket.YEARS,
                    bars = if (years.isEmpty()) emptyList()
                    else (years.min()..years.max()).map { Trend.Bar(it, byYear[it.toLong()] ?: Money.ZERO) },
                )
            }
            else -> {
                val byMonth = incomeByMonth(inPeriod, zone).associate { it.monthKey to it.total }
                val first = monthKeyOf(period.from!!)
                // until is exclusive, so the last day inside the period names the last month.
                val last = monthKeyOf(period.until!!.minusDays(1))
                Trend(
                    bucket = Trend.Bucket.MONTHS,
                    bars = (first..last).map { Trend.Bar(it, byMonth[it] ?: Money.ZERO) },
                )
            }
        }
    }

    fun incomeByStudent(classes: List<TutoringClass>): Map<Long, Money> =
        sumChargesBy(classes) { it.studentId }

    fun incomeBySubject(classes: List<TutoringClass>): Map<Long, Money> =
        sumChargesBy(classes) { it.subjectId }

    /**
     * Completed-class income from each group's lessons, keyed by group.
     *
     * Individual lessons are not in it at all — not as a residual line either — so the section it
     * fills sums to the group half of the income rather than to the whole. That is what makes it
     * readable next to [incomeByKind]: the kinds say how the income divides, this says which groups
     * the group half came from. A tutor deciding whether to keep running a group needs the group's
     * own number, and «Доход по ученикам» cannot give it: a member's line mixes their group lessons
     * with the individual ones they also take.
     */
    fun incomeByGroup(classes: List<TutoringClass>): Map<Long, Money> =
        sumChargesBy(classes.filter { it.groupId != null }) { it.groupId!! }

    /**
     * What share of [whole] [part] is, as a whole percent rounded half-up — 0 when there is no whole.
     *
     * Integer arithmetic on minor units, for the same reason money itself is integral: a percentage
     * on the screen should be derived from the kopecks it describes, not from a float that nearly is.
     *
     * Rounded shares are **not** made to add up to 100. Forcing the last line to absorb the remainder
     * would print a percentage that its own amount does not come to, and a tutor checking one line
     * against the total would find the app lying about the smallest number on the screen.
     */
    fun percentOf(part: Money, whole: Money): Int {
        if (whole.minor <= 0) return 0
        return ((part.minor * 100 + whole.minor / 2) / whole.minor).toInt()
    }

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

    private fun monthKeyOf(c: TutoringClass, zone: ZoneId): Int =
        monthKeyOf(c.start.atZone(zone).toLocalDate())

    private fun monthKeyOf(date: LocalDate): Int = date.year * 12 + (date.monthValue - 1)
}
