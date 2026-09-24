package com.seblful.klas.domain

import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a report of the tutor's income is. Income counts only the charges captured on completed classes
 * (so it matches what was actually billed); cancelled and scheduled classes contribute nothing.
 *
 * **[of] is the way in.** It takes the database's lists and a period and returns the whole [Report] —
 * which classes count, how each breakdown is ordered, which sections are worth showing at all. The
 * aggregations below are its parts and are tested directly, one rule at a time; they are not a second
 * way to assemble a report. A caller that reaches for them is deciding something this object should
 * have decided, and `ReportsSurfaceTest` says so.
 *
 * Each aggregation takes the classes it should count, so *which* ones is one decision made once, in
 * [of], rather than a filter each could disagree about. [trend] is the exception and says why.
 */
object Reports {

    /** Income for one calendar month, keyed as `year * 12 + (monthValue - 1)` (newest first). */
    data class MonthlyIncome(val monthKey: Int, val total: Money)

    /** A student's outstanding debt (only students who actually owe appear). */
    data class Debt(val student: StudentId, val amount: Money)

    /**
     * **What is owed, as a whole**: how many students are in the red and what it adds up to.
     *
     * A named answer rather than two calls to the aggregations below, because there is a second reader of
     * those numbers now — the weekly debt digest — and it is outside a report. `ReportsSurfaceTest` exists
     * to stop exactly that: a caller reaching past [of] for pieces of a report and reassembling them puts
     * the decisions about what a report *is* somewhere a report is not tested. The digest wants a summary
     * and not a report, so the summary is a thing this module names.
     *
     * A [total] with no [students] behind it is not expressible here, which is the useful part: the two
     * figures always come from one pass over one list of balances, so the tray cannot say «0 учеников ·
     * 540 BYN».
     */
    data class Outstanding(val students: Int, val total: Money) {

        companion object {
            /**
             * Sums [balances] — which must already be the students the caller means, since who counts is
             * not this type's rule. The debt digest passes the **active** ones, by the same rule the
             * reports follow: archiving is how a tutor says they have stopped teaching somebody.
             */
            fun of(balances: List<StudentBalance>): Outstanding =
                Outstanding(students = debts(balances).size, total = totalOutstanding(balances))
        }
    }

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
        data class Bar(val key: Int, val total: Money) {
            /** The month this bar covers (0–11) — the key's encoding is this module's to unpack. */
            val monthIndex: Int get() = key % 12
        }

        /** The best bucket — what every bar's height is a fraction of, and the one worth naming. */
        val peak: Bar? get() = bars.maxByOrNull { it.total.minor }?.takeUnless { it.total.isZero }

        /**
         * [bar]'s height as a fraction of [peak] — the chart is about *relative* size, which is what
         * lets it need no value axis. A statement about the report rather than about drawing, so it
         * lives here with [rows] and [peak] instead of in the composable; zero when nothing earned.
         */
        fun shareOf(bar: Bar): Float =
            peak?.let { bar.total.minor.toFloat() / it.total.minor } ?: 0f

        /** Nothing a chart could show: no income at all, or a single bucket to compare with itself. */
        val isEmpty: Boolean get() = bars.size < 2 || peak == null

        /**
         * The same buckets as a list to read, **newest first and without the empty ones**.
         *
         * The chart and the list want opposite things from the same data, which is why both are stated
         * here. A bucket that earned nothing has to be *in* the chart — that is what makes a quiet
         * March read as a gap in the year rather than as an absence its neighbours close over — and has
         * no business in the list, where it would be a row of zeros. The screen used to spell that
         * second half itself, next to a comment explaining it, while the first half was tested here.
         */
        val rows: List<Bar> get() = bars.asReversed().filterNot { it.total.isZero }

        companion object {
            val NONE = Trend(Bucket.MONTHS, emptyList())
        }
    }

    /**
     * One line of a report: a name out of the database, an amount, and its share of the whole where
     * there is a whole for it to be a share of.
     *
     * @param percent null where a share would be a lie — the lines of a section that does not partition
     *   the income (groups) or that measures something else entirely (debts) are not parts of one whole.
     * @param studentId set only where the line **is** a person the app can open: the debts. It is the
     *   most action-shaped list on the screen ("who owes me") and was the one that went nowhere.
     * @param share the line's ranking bar, as a fraction of its **section's largest** line — the bar
     *   pictures the ranking the rows are already in, where scaling to the total would leave a long
     *   tail of near-empty tracks. Null for a section of one: there is nothing to rank it against,
     *   and a bar filled to the brim would only assert that the only row is the biggest. Both rules
     *   used to be decided in the composable drawing the bar, i.e. nowhere a report test could see.
     */
    data class Line(
        val label: String,
        val amount: Money,
        val percent: Int? = null,
        val studentId: Long? = null,
        val share: Float? = null,
    )

    /**
     * One kind of teaching's income and share. Carries the [LessonKind] rather than a label, because
     * the two words for it are string resources and belong to the screen — every other line here is
     * labelled by a name that came out of the database.
     */
    data class KindLine(val kind: LessonKind, val amount: Money, val percent: Int)

    /**
     * A whole report, assembled: everything the reports screen draws, and nothing it has to work out.
     *
     * This exists because the decisions were split. The aggregations below returned raw maps and the
     * view model restated their filter and their order; the "a single kind is not a section" rule lived
     * at the call site; the trend's rows were re-derived in a composable. Each of those is a statement
     * about what a report *is*, and none of them was where the report is tested.
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
     * The whole report for [period], out of every class, student and payment there is.
     *
     * Takes the *unfiltered* lists on purpose. Which classes a report counts is one of its rules —
     * active students only, then the period — and a caller that filtered first would be deciding it.
     * That is also why the student labels are derived here rather than passed: a name table built from
     * all the students, paired with a breakdown built from the active ones, would draw a real amount
     * against a student the report is meant to be leaving out.
     *
     * [subjectNames] and [groupNames] do come in, because they are names and this has no database. They
     * are keyed by the typed ids of `model/Ids.kt`, so the compiler pairs each with its own breakdown.
     */
    fun of(
        period: ReportPeriod,
        classes: List<TutoringClass>,
        students: List<Student>,
        payments: List<Payment>,
        subjectNames: Map<SubjectId, String>,
        groupNames: Map<GroupId, String>,
        zone: ZoneId,
        /** Today, by the app's clock — what says whether [period] is still going. See [Report.changeToDate]. */
        today: LocalDate,
    ): Report {
        // Reports cover active students only: an archived student is retired, so their classes, income
        // and debts should no longer appear in totals or breakdowns.
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

        // The period before this one, filtered the same way. `shifted` returns the period itself for
        // all time, which is what makes "there is nothing to compare against" the same test as "the
        // arrows do nothing" — one fact about the period, stated once.
        val previous = period.shifted(-1).takeIf { period.isNavigable }
        // While the period is still going, both sides are measured over the days it has had so far — this
        // one's first N days against the previous one's first N. A finished period is compared whole.
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
            // Only worth a section when there is a split to see; a tutor who teaches one way would
            // otherwise get a heading over one line repeating the total above it.
            byKind = incomeByKind(inPeriod)
                .takeIf { it.size > 1 }
                .orEmpty()
                .map { KindLine(it.kind, it.total, percentOf(it.total, total)) },
            // No share: a group's lessons are only part of the income, so «38 %» here would read as a
            // share of everything while summing to the group half — the kind split above says that.
            byGroup = incomeByGroup(inPeriod).ranked(whole = null) { groupNames[it] },
            bySubject = incomeBySubject(inPeriod).ranked(total) { subjectNames[it] },
            byStudent = incomeByStudent(inPeriod).ranked(total) { studentNames[it] },
            // No share either: what matters about a debt is the money and who owes it, and a percentage
            // of the arrears is not something to act on. The id rides along so the row can open the
            // person — it is the one list here that names somebody to go and talk to.
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
        // The largest row is what every bar is a fraction of — see [Line.share].
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
    fun incomeByMonth(classes: List<TutoringClass>, zone: ZoneId): List<MonthlyIncome> =
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
        Billing.billable(classes)
            .mapNotNull { c -> key(c)?.let { it to c } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> Billing.charged(list) }

    private fun monthKeyOf(c: TutoringClass, zone: ZoneId): Int =
        monthKeyOf(c.start.atZone(zone).toLocalDate())

    private fun monthKeyOf(date: LocalDate): Int = date.year * 12 + (date.monthValue - 1)
}
