package com.seblful.klas.domain.reports

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
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
 * Builds reports from completed lessons and payments, including archived students. [of] applies the
 * period and assembles breakdowns; [trend] also supplies empty time buckets for the chart.
 */
object Reports {

    /** Income for one calendar month, keyed as `year * 12 + (monthValue - 1)` (newest first). */
    data class MonthlyIncome(val monthKey: Int, val total: Money)

    /** A student's outstanding debt (only students who actually owe appear). */
    data class Debt(val student: StudentId, val amount: Money)

    /**
     * The number of students in debt, the total they owe, and [debts] — who, most owed first.
     */
    data class Outstanding(val students: Int, val total: Money, val debts: List<Debt> = emptyList()) {

        companion object {
            /**
             * Summarizes the balances supplied by the caller.
             */
            fun of(balances: List<StudentBalance>): Outstanding {
                val debts = debts(balances)
                return Outstanding(students = debts.size, total = totalOutstanding(balances), debts = debts)
            }
        }
    }

    /**
     * What was **paid** in a period — money in hand — as against [Report.totalIncome], which is what was
     * earned. The two differ by every payment in advance and every debt, which is why they are two cards
     * and two sections of the export (D6) rather than one number.
     */
    data class Received(val total: Money, val byMethod: List<MethodLine>) {
        /** Whether the split says anything: not when every payment left the method unsaid. */
        val showsMethods: Boolean get() = byMethod.any { it.method != null }

        companion object {
            val NONE = Received(Money.ZERO, emptyList())
        }
    }

    /** What one way of paying brought in; [method] null for payments that did not say. */
    data class MethodLine(val method: PaymentMethod?, val amount: Money)

    /**
     * [payments] made inside [period], by the day they land on in [zone]; by method in the order the
     * methods are offered, the unsaid last.
     */
    fun received(payments: List<Payment>, period: ReportPeriod, zone: TimeZone): Received {
        val inPeriod = payments.filter { period.contains(it.date, zone) }
        val byMethod = inPeriod.groupBy { it.method }
            .map { (method, paid) -> MethodLine(method, Money(paid.sumOf { it.amount.minor })) }
            .filter { it.amount.minor != 0L }
            .sortedBy { it.method?.ordinal ?: Int.MAX_VALUE }
        return Received(Money(inPeriod.sumOf { it.amount.minor }), byMethod)
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
     * @param id the student, subject or group the line is about — unique within its section, so a list
     *   can follow a line when the ranking reorders.
     * @param percent null where a share would be a lie — the lines of a section that does not partition
     *   the income (groups) or that measures something else entirely (debts) are not parts of one whole.
     * @param studentId the student to open for a debt line.
     * @param share the amount relative to the largest line in the section, or null when there is no ranking.
     */
    data class Line(
        val id: Long,
        val label: String,
        val amount: Money,
        val percent: Int? = null,
        val studentId: Long? = null,
        val share: Float? = null,
    )

    /**
     * One line of the ledger: a lesson a student was billed for, or a payment they made, on [day].
     *
     * @param subject the lesson's subject, null for a payment.
     * @param method how a payment was made, null for a lesson or a payment that did not say.
     */
    data class LedgerEntry(
        val day: LocalDate,
        val student: String,
        val subject: String?,
        val payment: Boolean,
        val method: PaymentMethod?,
        val amount: Money,
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
        /** What was paid in [period] — see [Received]. */
        val received: Received = Received.NONE,
        /** Every taught lesson and every payment in [period], by day — the ledger export, [ReportCsv.buildLedger]. */
        val ledger: List<LedgerEntry> = emptyList(),
    ) {
        /** The period earned nothing — the sections below the total have nothing to break down. */
        val noIncome: Boolean get() = totalIncome.isZero
    }

    /**
     * Builds [period]'s report, retaining archived students' financial history. Source lists must be
     * unfiltered; only income is clipped to the period, while debt spans all history.
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
        val studentIds = students.map { it.id }.toSet()
        return of(
            period = period,
            classes = classes,
            students = students,
            // A balance spans a student's whole history, so it is built from every class and payment —
            // clipping the charges to a period while the payments stay whole would invent credit.
            balances = Billing.balancesFor(students, classes, payments),
            hasData = classes.any { it.studentId in studentIds } || payments.any { it.studentId in studentIds },
            payments = payments,
            subjectNames = subjectNames,
            groupNames = groupNames,
            zone = zone,
            today = today,
        )
    }

    /**
     * The days a report on [period] reads classes from: the period and the one [Report.change] compares
     * it with. Null for all time, which reads every class.
     */
    fun window(period: ReportPeriod): ReportPeriod.Bounds? {
        val bounds = period.bounds ?: return null
        val from = period.shifted(-1).bounds?.from ?: bounds.from
        return ReportPeriod.Bounds(minOf(from, bounds.from), bounds.untilExclusive)
    }

    /**
     * [of] from what a database can sum: [classes] need only cover [window], [balances] are each student's
     * over their whole history, and [hasData] says whether any student has a class or payment.
     */
    fun of(
        period: ReportPeriod,
        classes: List<TutoringClass>,
        students: List<Student>,
        balances: List<StudentBalance>,
        hasData: Boolean,
        subjectNames: Map<SubjectId, String>,
        groupNames: Map<GroupId, String>,
        zone: TimeZone,
        today: LocalDate,
        /** Payments covering at least [period]; those of students not in [students] are left out. */
        payments: List<Payment> = emptyList(),
    ): Report {
        val studentIds = students.map { it.id }.toSet()
        val knownClasses = classes.filter { it.studentId in studentIds }
        val studentNames = students.associate { StudentId(it.id) to it.name }
        val knownBalances = balances.filter { it.studentId in studentIds }

        val inPeriod = period.filter(knownClasses, zone)
        val total = totalIncome(inPeriod)

        val previous = period.shifted(-1).takeIf { period.isNavigable }
        val elapsed = period.daysElapsed(today)
        val changeNow = elapsed?.let { period.firstDays(it) }?.let { totalIncome(it.filter(inPeriod, zone)) } ?: total
        val changeBefore = previous?.let { p ->
            val days = elapsed?.let { p.firstDays(it) } ?: p.bounds
            totalIncome(days?.filter(knownClasses, zone) ?: p.filter(knownClasses, zone))
        }

        return Report(
            period = period,
            hasData = hasData,
            totalIncome = total,
            taught = Taught.overall(inPeriod),
            trend = trend(knownClasses, period, zone),
            byKind = incomeByKind(inPeriod)
                .takeIf { it.size > 1 }
                .orEmpty()
                .map { KindLine(it.kind, it.total, percentOf(it.total, total)) },
            byGroup = incomeByGroup(inPeriod).ranked(whole = null, idOf = { it.value }) { groupNames[it] },
            bySubject = incomeBySubject(inPeriod).ranked(total, idOf = { it.value }) { subjectNames[it] },
            byStudent = incomeByStudent(inPeriod).ranked(total, idOf = { it.value }) { studentNames[it] },
            debts = debts(knownBalances).map {
                Line(
                    id = it.student.value,
                    label = studentNames[it.student].orEmpty(),
                    amount = it.amount,
                    studentId = it.student.value,
                )
            },
            totalOutstanding = totalOutstanding(knownBalances),
            change = change(changeNow, changeBefore),
            changeToDate = elapsed != null && previous != null,
            previousPeriod = previous,
            received = received(payments.filter { it.studentId in studentIds }, period, zone),
            ledger = ledger(
                Billing.billable(inPeriod),
                payments.filter { it.studentId in studentIds && period.contains(it.date, zone) },
                studentNames,
                subjectNames,
                zone,
            ),
        )
    }

    /**
     * [classes] billed and [payments] made, one entry each, by day — a lesson before a payment on the same
     * day, each by its time: the same report whatever order its rows came in.
     */
    private fun ledger(
        classes: List<TutoringClass>,
        payments: List<Payment>,
        studentNames: Map<StudentId, String>,
        subjectNames: Map<SubjectId, String>,
        zone: TimeZone,
    ): List<LedgerEntry> {
        val lessons = classes.sortedWith(compareBy({ it.start }, { it.id }, { it.studentId })).map {
            LedgerEntry(
                day = it.start.toLocalDateTime(zone).date,
                student = studentNames[StudentId(it.studentId)].orEmpty(),
                subject = subjectNames[SubjectId(it.subjectId)],
                payment = false,
                method = null,
                amount = it.charge,
            )
        }
        val paid = payments.sortedWith(compareBy({ it.date }, { it.id }, { it.studentId }, { it.amount.minor })).map {
            LedgerEntry(
                day = it.date.toLocalDateTime(zone).date,
                student = studentNames[StudentId(it.studentId)].orEmpty(),
                subject = null,
                payment = true,
                method = it.method,
                amount = it.amount,
            )
        }
        return (lessons + paid).sortedWith(compareBy<LedgerEntry> { it.day }.thenBy { it.payment })
    }

    /**
     * A breakdown as lines: zero and unpriced entries dropped, largest first.
     *
     * @param whole what these lines are parts of, or null when they are not parts of anything — see
     *   [Line.percent].
     */
    private fun <K> Map<K, Money>.ranked(whole: Money?, idOf: (K) -> Long, nameOf: (K) -> String?): List<Line> {
        val rows = entries
            .filter { it.value.minor > 0 }
            .sortedByDescending { it.value.minor }
        val top = rows.firstOrNull()?.value?.minor?.coerceAtLeast(1)
        return rows.map {
            Line(
                id = idOf(it.key),
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
        data class Percent(val value: Long) : Change
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
        return Change.Percent(if (delta < 0) -magnitude else magnitude)
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
