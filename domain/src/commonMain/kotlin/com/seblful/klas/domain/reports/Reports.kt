package com.seblful.klas.domain.reports

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Builds income reports from completed lessons and payments. [of] applies the active-student and period
 * rules and assembles the breakdowns; [trend] also supplies empty time buckets for the chart.
 */
object Reports {

    /** Income for one calendar month, keyed as `year * 12 + (monthValue - 1)` (newest first). */
    data class MonthlyIncome(val monthKey: Int, val total: Money)

    /** A student's outstanding debt (only students who actually owe appear). */
    data class Debt(val student: StudentId, val amount: Money)

    /**
     * The number of students in debt and the total they owe.
     */
    data class Outstanding(val students: Int, val total: Money) {

        companion object {
            /**
             * Summarizes the balances supplied by the caller.
             */
            fun of(balances: List<StudentBalance>): Outstanding =
                Outstanding(students = debts(balances).size, total = totalOutstanding(balances))
        }
    }

    /** Income from one kind of teaching — see [incomeByKind]. */
    data class KindIncome(val kind: LessonKind, val total: Money)

    /**
     * Income bars by month or year, **oldest first**, including buckets with no income. Periods use
     * months; all-time reports use years.
     */
    data class Trend(val bucket: Bucket, val bars: List<Bar>) {

        /** What one bar covers. */
        enum class Bucket { MONTHS, YEARS }

        /**
         * @param key a month key (`year * 12 + monthIndex`) when the bucket is [Bucket.MONTHS], a
         *   plain year when it is [Bucket.YEARS].
         */
        data class Bar(val key: Int, val total: Money) {
            /** The month this bar covers (0–11) — the key's encoding is this module's to unpack. */
            val monthIndex: Int get() = key % 12
        }

        /** The best bucket — what every bar's height is a fraction of, and the one worth naming. */
        val peak: Bar? get() = bars.maxByOrNull { it.total.minor }?.takeUnless { it.total.isZero }

        /**
         * [bar]'s height as a fraction of [peak], or zero when no income was earned.
         */
        fun shareOf(bar: Bar): Float =
            peak?.let { bar.total.minor.toFloat() / it.total.minor } ?: 0f

        /** Nothing a chart could show: no income at all, or a single bucket to compare with itself. */
        val isEmpty: Boolean get() = bars.size < 2 || peak == null

        /**
         * Nonzero buckets in reverse chronological order, for the report list.
         */
        val rows: List<Bar> get() = bars.asReversed().filterNot { it.total.isZero }

        companion object {
            val NONE = Trend(Bucket.MONTHS, emptyList())
        }
    }

    /**
     * One report line, with its label and amount.
     *
     * @param percent null where a share would be a lie — the lines of a section that does not partition
     *   the income (groups) or that measures something else entirely (debts) are not parts of one whole.
     * @param studentId the student to open for a debt line.
     * @param share the amount relative to the largest line in the section, or null when there is no ranking.
     */
    data class Line(
        val label: String,
        val amount: Money,
        val percent: Int? = null,
        val studentId: Long? = null,
        val share: Float? = null,
    )

    /**
     * Income for one kind of teaching.
     */
    data class KindLine(val kind: LessonKind, val amount: Money, val percent: Int)

    /**
     * The report data and breakdowns for one period.
     */
    data class Report(
        /** The slice of time every income number covers. [debts] are the documented exception. */
        val period: ReportPeriod,
        /**
         * Whether there is any lesson or payment at all — false only before the first one. Kept apart
         * from "this period earned nothing", so an empty year still shows the picker that gets the tutor
         * out of it rather than a dead end saying there is no data.
         */
        val hasData: Boolean = false,
        val totalIncome: Money = Money.ZERO,
        /** How much teaching the period holds — a group lesson counted once. See [Taught.overall]. */
        val taught: Taught.Totals = Taught.Totals.NONE,
        val trend: Trend = Trend.NONE,
        /** Empty unless **both** kinds earned something: a single line would only restate the total. */
        val byKind: List<KindLine> = emptyList(),
        val byGroup: List<Line> = emptyList(),
        val bySubject: List<Line> = emptyList(),
        val byStudent: List<Line> = emptyList(),
        val debts: List<Line> = emptyList(),
        val totalOutstanding: Money = Money.ZERO,
        /**
         * How this period's income compares with the one before it, or null when there is nothing to
         * compare against — all time has no predecessor, and neither does a period before the first
         * lesson (which is [Change.NoBaseline] rather than null: there *was* a period, it earned
         * nothing, and saying so is more use than saying nothing).
         */
        val change: Change? = null,
        /**
         * Whether [change] measured only the days the period has had so far, against as many of the
         * previous one's — true while the period is still going, so the screen can say «за те же дни».
         */
        val changeToDate: Boolean = false,
        /**
         * The period being compared against, so the screen can name it — «+12 % к периоду "Июнь 2026"».
         * The period rather than a formatted label, because naming one is a string resource.
         */
        val previousPeriod: ReportPeriod? = null,
    ) {
        /** The period earned nothing — the sections below the total have nothing to break down. */
        val noIncome: Boolean get() = totalIncome.isZero
    }

    /**
     * Builds [period]'s report. [classes], [students], and [payments] must be unfiltered; this function
     * includes active students only and applies the period to their income. Names are supplied by the caller.
     */
    fun of(
        period: ReportPeriod,
        classes: List<TutoringClass>,
        students: List<Student>,
        payments: List<Payment>,
        subjectNames: Map<SubjectId, String>,
        groupNames: Map<GroupId, String>,
        zone: TimeZone,
        /** Today, by the app's clock — what says whether [period] is still going. See [Report.changeToDate]. */
        today: LocalDate,
    ): Report {
        val activeStudents = students.filter { !it.archived }
        val activeIds = activeStudents.map { it.id }.toSet()
        val activeClasses = classes.filter { it.studentId in activeIds }
        val activePayments = payments.filter { it.studentId in activeIds }
        val studentNames = activeStudents.associate { StudentId(it.id) to it.name }

        // A balance spans a student's whole history, so it is built from every class and payment —
        // clipping the charges to a period while the payments stay whole would invent credit.
        val balances = Billing.balancesFor(activeStudents, activeClasses, activePayments)
        val inPeriod = period.filter(activeClasses, zone)
        val total = totalIncome(inPeriod)

        val previous = period.shifted(-1).takeIf { period.isNavigable }
        val elapsed = period.daysElapsed(today)
        val changeNow = elapsed?.let { period.firstDays(it) }?.let { totalIncome(it.filter(inPeriod, zone)) } ?: total
        val changeBefore = previous?.let { p ->
            val days = elapsed?.let { p.firstDays(it) } ?: p.bounds
            totalIncome(days?.filter(activeClasses, zone) ?: p.filter(activeClasses, zone))
        }

        return Report(
            period = period,
            hasData = activeClasses.isNotEmpty() || activePayments.isNotEmpty(),
            totalIncome = total,
            taught = Taught.overall(inPeriod),
            trend = trend(activeClasses, period, zone),
            byKind = incomeByKind(inPeriod)
                .takeIf { it.size > 1 }
                .orEmpty()
                .map { KindLine(it.kind, it.total, percentOf(it.total, total)) },
            byGroup = incomeByGroup(inPeriod).ranked(whole = null) { groupNames[it] },
            bySubject = incomeBySubject(inPeriod).ranked(total) { subjectNames[it] },
            byStudent = incomeByStudent(inPeriod).ranked(total) { studentNames[it] },
            debts = debts(balances).map {
                Line(
                    label = studentNames[it.student].orEmpty(),
                    amount = it.amount,
                    studentId = it.student.value,
                )
            },
            totalOutstanding = totalOutstanding(balances),
            change = change(changeNow, changeBefore),
            changeToDate = elapsed != null && previous != null,
            previousPeriod = previous,
        )
    }

    /**
     * A breakdown as lines: zero and unpriced entries dropped, largest first.
     *
     * @param whole what these lines are parts of, or null when they are not parts of anything — see
     *   [Line.percent].
     */
    private fun <K> Map<K, Money>.ranked(whole: Money?, nameOf: (K) -> String?): List<Line> {
        val rows = entries
            .filter { it.value.minor > 0 }
            .sortedByDescending { it.value.minor }
        val top = rows.firstOrNull()?.value?.minor?.coerceAtLeast(1)
        return rows.map {
            Line(
                label = nameOf(it.key).orEmpty(),
                amount = it.value,
                percent = whole?.let { w -> percentOf(it.value, w) },
                share = top?.let { t -> it.value.minor.toFloat() / t }.takeIf { rows.size > 1 },
            )
        }
    }

    fun totalIncome(classes: List<TutoringClass>): Money =
        Billing.charged(classes)

    /** Completed-class income grouped by the month the class took place in [zone], newest first. */
    fun incomeByMonth(classes: List<TutoringClass>, zone: TimeZone): List<MonthlyIncome> =
        Billing.billable(classes)
            .groupBy { monthKeyOf(it, zone) }
            .map { (key, list) -> MonthlyIncome(key, Billing.charged(list)) }
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
    fun trend(classes: List<TutoringClass>, period: ReportPeriod, zone: TimeZone): Trend {
        val inPeriod = period.filter(classes, zone)
        return when (val bounds = period.bounds) {
            null -> {
                val byYear = sumChargesBy(inPeriod) { it.start.toLocalDateTime(zone).date.year }
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
                val last = monthKeyOf(bounds.lastDay)
                Trend(
                    bucket = Trend.Bucket.MONTHS,
                    bars = (first..last).map { Trend.Bar(it, byMonth[it] ?: Money.ZERO) },
                )
            }
        }
    }

    // Typed ids keep each breakdown paired with the matching name map.

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
        Billing.billable(classes)
            .groupBy { LessonKind.of(it) }
            .map { (kind, list) -> KindIncome(kind, Billing.charged(list)) }
            .filterNot { it.total.isZero }
            .sortedByDescending { it.total.minor }

    /**
     * How this period's income compares with the period before it.
     *
     * The reports screen exists to answer "how am I doing", and a bare «45 000 BYN» does not: without
     * something to hold it against, the biggest number on the screen is a fact and not an answer. One
     * signed percentage is the cheapest thing that turns it into one.
     *
     * Integer arithmetic on minor units throughout, for the reason [percentOf] gives.
     */
    sealed interface Change {

        /**
         * The period before earned nothing, so there is no ratio to state. Deliberately not "+100 %"
         * or "+∞": growth from zero is not a percentage, and printing one would be arithmetic
         * theatre. The screen says the previous period was empty instead, which is the actual news.
         */
        data object NoBaseline : Change

        /** Signed: positive for more than last time, negative for less, zero for level. */
        data class Percent(val value: Int) : Change
    }

    /**
     * [current] against [previous]. Null when there is nothing to compare against — all time has no
     * period before it.
     */
    fun change(current: Money, previous: Money?): Change? {
        if (previous == null) return null
        if (previous.minor <= 0) return Change.NoBaseline
        val delta = current.minor - previous.minor
        // Rounded away from zero at the half, so a 0.5 % rise reads as 1 % rather than as no change.
        val magnitude = (kotlin.math.abs(delta) * 100 + previous.minor / 2) / previous.minor
        return Change.Percent(if (delta < 0) (-magnitude).toInt() else magnitude.toInt())
    }

    /** Students with a positive balance (owe money), largest debt first. */
    fun debts(balances: List<StudentBalance>): List<Debt> =
        balances.mapNotNull { b -> (b.state as? Balance.Owed)?.let { Debt(StudentId(b.studentId), it.amount) } }
            .sortedByDescending { it.amount.minor }

    /** Sum of every positive balance — total money owed to the tutor (credits excluded). */
    fun totalOutstanding(balances: List<StudentBalance>): Money =
        debts(balances).fold(Money.ZERO) { acc, d -> acc + d.amount }

    private inline fun <K> sumChargesBy(classes: List<TutoringClass>, key: (TutoringClass) -> K?): Map<K, Money> =
        Billing.billable(classes)
            .mapNotNull { c -> key(c)?.let { it to c } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> Billing.charged(list) }

    private fun monthKeyOf(c: TutoringClass, zone: TimeZone): Int =
        monthKeyOf(c.start.toLocalDateTime(zone).date)

    private fun monthKeyOf(date: LocalDate): Int = date.year * 12 + (date.month.number - 1)
}
