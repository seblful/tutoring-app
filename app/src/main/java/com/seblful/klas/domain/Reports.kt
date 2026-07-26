package com.seblful.klas.domain

import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.SubjectId
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
    data class Debt(val student: StudentId, val amount: Money)

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
        // Branching on the bounds rather than on the kind: "all time" and "has no bounds to
        // enumerate" are the same statement, and this way the months arm gets them non-null.
        return when (val bounds = period.bounds) {
            // All time has no bound to enumerate, so the data sets it: first earning year to last.
            null -> {
                // Keyed by the year itself: a bucket is not an id, and now that the sum is generic it
                // no longer has to be widened to a Long and narrowed back to index the result.
                val byYear = sumChargesBy(inPeriod) { it.start.atZone(zone).toLocalDate().year }
                val years = byYear.keys
                Trend(
                    bucket = Trend.Bucket.YEARS,
                    bars = if (years.isEmpty()) emptyList()
                    else (years.min()..years.max()).map { Trend.Bar(it, byYear[it] ?: Money.ZERO) },
                )
            }
            else -> {
                val byMonth = incomeByMonth(inPeriod, zone).associate { it.monthKey to it.total }
                val first = monthKeyOf(bounds.from)
                // until is exclusive, so the last day inside the period names the last month.
                val last = monthKeyOf(bounds.lastDay)
                Trend(
                    bucket = Trend.Bucket.MONTHS,
                    bars = (first..last).map { Trend.Bar(it, byMonth[it] ?: Money.ZERO) },
                )
            }
        }
    }

    // The three breakdowns below are keyed by *typed* ids rather than by `Long`. They are otherwise
    // identical in shape, and the screen pairs each with its own table of names — see `model/Ids.kt`
    // for what that used to allow, and why this is the one place in the app where ids carry a type.

    fun incomeByStudent(classes: List<TutoringClass>): Map<StudentId, Money> =
        sumChargesBy(classes) { StudentId(it.studentId) }

    fun incomeBySubject(classes: List<TutoringClass>): Map<SubjectId, Money> =
        sumChargesBy(classes) { SubjectId(it.subjectId) }

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
    fun incomeByGroup(classes: List<TutoringClass>): Map<GroupId, Money> =
        // An individual class keys to null and is dropped, which is the whole filter.
        sumChargesBy(classes) { c -> c.groupId?.let { GroupId(it) } }

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
            .map { Debt(StudentId(it.studentId), it.balance) }
            .sortedByDescending { it.amount.minor }

    /** Sum of every positive balance — total money owed to the tutor (credits excluded). */
    fun totalOutstanding(balances: List<StudentBalance>): Money =
        balances.fold(Money.ZERO) { acc, b -> if (b.balance.minor > 0) acc + b.balance else acc }

    /**
     * Billable charges summed per key. A **null** key means "this class has no such thing" and drops
     * the class from the result — which is how [incomeByGroup] states its filter once, in the key it
     * groups by, rather than as a `filter` and a matching `!!` that have to be kept in step.
     */
    private inline fun <K> sumChargesBy(classes: List<TutoringClass>, key: (TutoringClass) -> K?): Map<K, Money> =
        classes.filter { it.status.isBillable }
            .mapNotNull { c -> key(c)?.let { it to c } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.fold(Money.ZERO) { a, c -> a + c.charge } }

    private fun monthKeyOf(c: TutoringClass, zone: ZoneId): Int =
        monthKeyOf(c.start.atZone(zone).toLocalDate())

    private fun monthKeyOf(date: LocalDate): Int = date.year * 12 + (date.monthValue - 1)
}
