package com.seblful.klas.domain.reports

import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportsTest {

    private val zone = ZoneId.of("Europe/Minsk")

    /** The epoch-month key the trend and `incomeByMonth` bucket by. */
    private fun keyOf(year: Int, month: Int) = year * 12 + (month - 1)

    private fun klass(
        studentId: Long,
        subjectId: Long,
        start: String,
        charge: Long,
        status: LessonStatus = LessonStatus.COMPLETED,
        groupId: Long? = null,
    ) = TutoringClass(
        id = nextId,
        lessonId = lessonIdFor(groupId, start, nextId++),
        studentId = studentId, subjectId = subjectId, start = Instant.parse(start),
        durationMinutes = 60, status = status, charge = Money(charge), groupId = groupId,
    )

    private var nextId = 1L

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    /**
     * The lesson a fixture's class belongs to: one per (group, start) for a group's, its own id for an
     * individual class. A convention of the test — storage has a column for it — and it has to be stated
     * because that column is the whole of what makes classes one lesson (`SessionRules.sessions`).
     */
    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    /**
     * A whole report of [classes], with one student who is never archived and no payments — read long
     * after the period, unless [today] says otherwise, so a comparison is of whole periods.
     */
    private fun reportOf(
        classes: List<TutoringClass>,
        period: ReportPeriod,
        today: LocalDate = LocalDate.of(2100, 1, 1),
    ) = Reports.of(
        period = period,
        classes = classes,
        students = classes.map { it.studentId }.distinct().map { Student(id = it, name = "S$it") },
        payments = emptyList(),
        subjectNames = classes.associate { SubjectId(it.subjectId) to "П${it.subjectId}" },
        groupNames = classes.mapNotNull { it.groupId }.associate { GroupId(it) to "Г$it" },
        zone = zone,
        today = today,
    )

    /**
     * The kind split is only a section when there is a split to see. A tutor who teaches one way would
     * otherwise get a heading over a single line repeating the total right above it — and the line would
     * be «100 %», which says nothing at all.
     *
     * This rule used to sit at the call site in `ReportsViewModel`, where nothing tested it.
     */
    @Test
    fun `one kind of teaching is not a section`() {
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 5))
        val individualOnly = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T10:00:00Z", 3000),
        )

        assertEquals(
            "a heading over one line saying what the total already says",
            emptyList<Reports.KindLine>(),
            reportOf(individualOnly, period).byKind,
        )

        val bothKinds = individualOnly + klass(3, 10, "2026-05-12T10:00:00Z", 2000, groupId = 7)
        val split = reportOf(bothKinds, period).byKind
        assertEquals("both kinds earned, so the split is worth showing", 2, split.size)
        assertEquals("largest first", LessonKind.INDIVIDUAL, split.first().kind)
        assertEquals(Money(8000), split.first().amount)
        assertEquals(80, split.first().percent)
        assertEquals(20, split.last().percent)
    }

    @Test
    fun `total income counts only completed charges`() {
        val classes = listOf(
            klass(1, 1, "2026-05-10T10:00:00Z", 5000),
            klass(1, 1, "2026-05-11T10:00:00Z", 5000, LessonStatus.CANCELLED),
            klass(1, 1, "2026-05-12T10:00:00Z", 5000, LessonStatus.SCHEDULED),
        )
        assertEquals(Money(5000), Reports.totalIncome(classes))
    }

    @Test
    fun `income by month groups completed classes by calendar month`() {
        val classes = listOf(
            klass(1, 1, "2026-05-10T10:00:00Z", 5000),
            klass(1, 1, "2026-05-20T10:00:00Z", 3000),
            klass(2, 1, "2026-06-01T10:00:00Z", 4000),
        )
        val byMonth = Reports.incomeByMonth(classes, zone)
        // newest month first
        assertEquals(2026 * 12 + 5, byMonth[0].monthKey) // June (monthValue 6 -> index 5)
        assertEquals(Money(4000), byMonth[0].total)
        assertEquals(2026 * 12 + 4, byMonth[1].monthKey) // May
        assertEquals(Money(8000), byMonth[1].total)
    }

    /**
     * Rescheduling a lesson that already earned must not *move* its income into the month it was
     * pushed to. [ClassRules.move] re-plans it instead — scheduled again, charge voided — so the month
     * it was taught in stops claiming it and no other month starts. It is recognized again when the
     * rescheduled lesson completes.
     *
     * Asserted through the report rather than on the class, because the month a charge lands in is
     * what a tutor actually reads, and "the charge is zero" alone does not say the old month released
     * it.
     */
    @Test
    fun `moving a completed lesson forward takes its income out of the month, without relocating it`() {
        val may = klass(1, 1, "2026-05-10T10:00:00Z", 5000)
        assertEquals(
            listOf(keyOf(2026, 5) to Money(5000)),
            Reports.incomeByMonth(listOf(may), zone).map { it.monthKey to it.total },
        )

        val moved = ClassRules.move(
            may,
            newStart = Instant.parse("2026-06-10T10:00:00Z"),
            now = Instant.parse("2026-05-20T10:00:00Z"),
        )

        assertEquals(LessonStatus.SCHEDULED, moved.status)
        assertEquals(
            "neither May nor June recognizes a lesson that has not happened yet",
            emptyList<Reports.MonthlyIncome>(),
            Reports.incomeByMonth(listOf(moved), zone),
        )
    }

    @Test
    fun `income by student and subject sum the right charges`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(1, 11, "2026-05-11T10:00:00Z", 3000),
            klass(2, 10, "2026-05-12T10:00:00Z", 4000),
        )
        assertEquals(Money(8000), Reports.incomeByStudent(classes)[StudentId(1)])
        assertEquals(Money(4000), Reports.incomeByStudent(classes)[StudentId(2)])
        assertEquals(Money(9000), Reports.incomeBySubject(classes)[SubjectId(10)])
        assertEquals(Money(3000), Reports.incomeBySubject(classes)[SubjectId(11)])
    }

    @Test
    fun `income by kind splits one-to-one from group teaching, largest first`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(3, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(4, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            // Neither of these was billed, so neither kind may count it.
            klass(1, 10, "2026-05-12T10:00:00Z", 5000, LessonStatus.CANCELLED),
            klass(2, 10, "2026-05-12T18:00:00Z", 2000, LessonStatus.SCHEDULED, groupId = 7),
        )

        val lines = Reports.incomeByKind(classes)

        // A group lesson's money is the sum of its members' charges — the group sets the rate, each
        // member owes their share of it — so three at 20 outweighs one at 50.
        assertEquals(listOf(LessonKind.GROUP, LessonKind.INDIVIDUAL), lines.map { it.kind })
        assertEquals(Money(6000), lines[0].total)
        assertEquals(Money(5000), lines[1].total)
        assertEquals(Reports.totalIncome(classes), lines.fold(Money.ZERO) { a, l -> a + l.total })
    }

    /** A tutor who teaches one way should not be shown a "split" that only restates the total. */
    @Test
    fun `a kind that earned nothing is left out rather than printed as zero`() {
        val onlyIndividual = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, LessonStatus.CANCELLED, groupId = 7),
        )

        assertEquals(
            listOf(LessonKind.INDIVIDUAL),
            Reports.incomeByKind(onlyIndividual).map { it.kind },
        )
        assertEquals(emptyList<Reports.KindIncome>(), Reports.incomeByKind(emptyList()))
    }

    @Test
    fun `income by group counts only group lessons, keyed by the group`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000), // individual: not in the breakdown at all
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(3, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(4, 10, "2026-05-12T18:00:00Z", 3000, groupId = 8),
            klass(5, 10, "2026-05-13T18:00:00Z", 9000, LessonStatus.CANCELLED, groupId = 8),
        )

        val byGroup = Reports.incomeByGroup(classes)

        // A group's line is the sum of its members' charges — the group set the rate, each owes a share.
        assertEquals(Money(4000), byGroup[GroupId(7)])
        assertEquals(Money(3000), byGroup[GroupId(8)])
        assertEquals(
            "individual lessons have no group to be counted under",
            setOf(GroupId(7), GroupId(8)),
            byGroup.keys,
        )
    }

    @Test
    fun `a percentage is rounded half-up and needs no whole to be safe`() {
        assertEquals(33, Reports.percentOf(Money(1000), Money(3000)))
        assertEquals(67, Reports.percentOf(Money(2000), Money(3000)))
        assertEquals(50, Reports.percentOf(Money(50), Money(100)))
        assertEquals(100, Reports.percentOf(Money(4000), Money(4000)))
        assertEquals(0, Reports.percentOf(Money(0), Money(4000)))
        // Nothing to be a share of: 0, not a division by zero.
        assertEquals(0, Reports.percentOf(Money(4000), Money.ZERO))
    }

    /** Rounded shares are allowed not to add up — see [Reports.percentOf]. */
    @Test
    fun `three equal shares each report a third, not a forced hundred`() {
        val third = Reports.percentOf(Money(1000), Money(3000))
        assertEquals(99, third * 3)
    }

    @Test
    fun `a year's trend is its twelve months, oldest first, quiet ones included`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 5000),
            klass(1, 10, "2026-02-20T10:00:00Z", 1000),
            klass(1, 10, "2026-11-10T10:00:00Z", 3000),
            klass(1, 10, "2025-06-10T10:00:00Z", 9900), // another year: outside the period
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals(Reports.Trend.Bucket.MONTHS, trend.bucket)
        assertEquals("a year is drawn as all twelve of its months", 12, trend.bars.size)
        assertEquals(keyOf(2026, 1), trend.bars.first().key)
        assertEquals(keyOf(2026, 12), trend.bars.last().key)
        assertEquals(Money(6000), trend.bars[1].total) // February
        assertEquals(Money.ZERO, trend.bars[2].total) // March, taught nothing
        assertEquals(Money(3000), trend.bars[10].total) // November
        assertEquals(keyOf(2026, 2), trend.peak?.key)
    }

    /**
     * The chart and the list read the same buckets in opposite directions, and both are the trend's own
     * statement. This half used to be a `filterNot` in a composable — so the rule that a fallow month is
     * a gap in the chart was tested and the rule that it is *not* a row in the list was not.
     */
    @Test
    fun `the rows are newest first and skip the months that earned nothing`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 5000),
            klass(1, 10, "2026-11-10T10:00:00Z", 3000),
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals("twelve bars in the chart", 12, trend.bars.size)
        assertEquals(
            "two rows in the list, November before February",
            listOf(keyOf(2026, 11) to Money(3000), keyOf(2026, 2) to Money(5000)),
            trend.rows.map { it.key to it.total },
        )
    }

    @Test
    fun `all time is drawn as years, from the first that earned to the last`() {
        val classes = listOf(
            klass(1, 10, "2024-03-10T10:00:00Z", 1000),
            klass(1, 10, "2026-03-10T10:00:00Z", 4000),
        )
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.ALL, YearMonth.of(2026, 7)), zone)

        assertEquals(Reports.Trend.Bucket.YEARS, trend.bucket)
        // 2025 earned nothing but is still a bar: a fallow year is part of the shape.
        assertEquals(listOf(2024, 2025, 2026), trend.bars.map { it.key })
        assertEquals(listOf(Money(1000), Money.ZERO, Money(4000)), trend.bars.map { it.total })
        assertEquals(2026, trend.peak?.key)
    }

    @Test
    fun `a single month has nothing to compare itself with`() {
        val classes = listOf(klass(1, 10, "2026-05-10T10:00:00Z", 5000))
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 5)), zone)

        assertEquals(1, trend.bars.size)
        assertTrue("one bar is already said by the total above it", trend.isEmpty)
    }

    @Test
    fun `a period that earned nothing has no chart, however many buckets it spans`() {
        val classes = listOf(klass(1, 10, "2026-05-10T10:00:00Z", 5000, LessonStatus.CANCELLED))
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 5)), zone)

        assertEquals(12, trend.bars.size)
        assertNull(trend.peak)
        assertTrue("twelve empty bars are not a chart", trend.isEmpty)
        assertTrue(Reports.trend(emptyList(), ReportPeriod(ReportPeriod.Kind.ALL, YearMonth.of(2026, 5)), zone).isEmpty)
    }

    /** The chart is drawn under the total, so it may never count something the total does not. */
    @Test
    fun `the trend adds up to the period's income`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 5000),
            klass(2, 10, "2026-11-11T18:00:00Z", 2000, groupId = 7),
            klass(1, 10, "2025-02-10T10:00:00Z", 8000), // last year: in neither
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 1))
        val trend = Reports.trend(classes, period, zone)

        assertEquals(
            Reports.totalIncome(period.filter(classes, zone)),
            trend.bars.fold(Money.ZERO) { a, b -> a + b.total },
        )
    }

    @Test
    fun `debts lists only students who owe, largest first`() {
        val balances = listOf(
            StudentBalance(1, charged = Money(5000), paid = Money(2000)), // owes 3000
            StudentBalance(2, charged = Money(4000), paid = Money(9000)), // credit -5000
            StudentBalance(3, charged = Money(10000), paid = Money(1000)), // owes 9000
        )
        val debts = Reports.debts(balances)
        assertEquals(listOf(StudentId(3), StudentId(1)), debts.map { it.student })
        assertEquals(Money(9000), debts[0].amount)
        assertEquals(Money(12000), Reports.totalOutstanding(balances)) // 3000 + 9000, credit excluded
    }

    /**
     * The ranked bar's two rules — scaled to the section's largest line, dropped for a section of
     * one — used to be decided by the composable that drew the bar. They are the report's own, so
     * they arrive on the line ([Reports.Line.share]).
     */
    @Test
    fun `a ranked line carries its bar as a fraction of the section's largest`() {
        val classes = listOf(
            klass(1, 10, "2026-06-10T10:00:00Z", 4000),
            klass(1, 20, "2026-06-11T10:00:00Z", 1000),
        )
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 6))

        val report = reportOf(classes, period)

        assertEquals(listOf(1.0f, 0.25f), report.bySubject.map { it.share })
    }

    @Test
    fun `a section of one line offers no bar — nothing to rank it against`() {
        val classes = listOf(klass(1, 10, "2026-06-10T10:00:00Z", 4000))
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 6))

        val report = reportOf(classes, period)

        assertNull(report.bySubject.single().share)
    }

    /** The chart's bar heights are the trend's own statement, like [Reports.Trend.rows]. */
    @Test
    fun `a bar's height is its fraction of the peak`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 6000),
            klass(1, 10, "2026-11-10T10:00:00Z", 3000),
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals(1.0f, trend.shareOf(trend.bars[1]))
        assertEquals(0.5f, trend.shareOf(trend.bars[10]))
        assertEquals("a quiet month is an empty track", 0f, trend.shareOf(trend.bars[2]))
        assertEquals("the key's encoding is the module's to unpack", 1, trend.bars[1].monthIndex)
    }

    /**
     * The screen opens on the year in progress, and its income so far used to be set against the whole of
     * the year before: in September, a tutor out-earning every month of last year read a fall. While the
     * period is going, both sides are its first N days.
     */
    @Test
    fun `a year in progress is compared with the same days of the year before`() {
        val classes = listOf(
            klass(1, 10, "2025-03-10T10:00:00Z", 10000), // inside last year's first 267 days
            klass(1, 10, "2025-11-10T10:00:00Z", 90000), // after them: not this year's to answer for yet
            klass(1, 10, "2026-03-10T10:00:00Z", 12000),
        )
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 9))

        val report = reportOf(classes, year, today = LocalDate.of(2026, 9, 24))

        assertEquals(Reports.Change.Percent(20), report.change)
        assertTrue("and it says it measured the days so far", report.changeToDate)
    }

    @Test
    fun `a finished year is compared whole`() {
        val classes = listOf(
            klass(1, 10, "2025-03-10T10:00:00Z", 10000),
            klass(1, 10, "2025-11-10T10:00:00Z", 10000),
            klass(1, 10, "2026-03-10T10:00:00Z", 30000),
        )
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 1))

        val report = reportOf(classes, year, today = LocalDate.of(2027, 2, 1))

        assertEquals(Reports.Change.Percent(50), report.change)
        assertEquals(false, report.changeToDate)
    }

    /** A month in progress, the same way — and a lesson later today is not part of "so far" yet. */
    @Test
    fun `a month in progress is compared with the same days of the month before`() {
        val classes = listOf(
            klass(1, 10, "2026-08-05T10:00:00Z", 4000),
            klass(1, 10, "2026-08-28T10:00:00Z", 4000),
            klass(1, 10, "2026-09-05T10:00:00Z", 6000),
        )
        val month = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 9))

        val report = reportOf(classes, month, today = LocalDate.of(2026, 9, 24))

        assertEquals(Reports.Change.Percent(50), report.change)
    }

    @Test
    fun `the days a period has had are counted from its first, and none once it is over`() {
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 1))

        assertEquals(1L, year.daysElapsed(LocalDate.of(2026, 1, 1)))
        assertEquals(267L, year.daysElapsed(LocalDate.of(2026, 9, 24)))
        assertNull("over", year.daysElapsed(LocalDate.of(2027, 1, 1)))
        assertNull("not begun", year.daysElapsed(LocalDate.of(2025, 12, 31)))
        assertNull("all time has no days", ReportPeriod(ReportPeriod.Kind.ALL, YearMonth.of(2026, 1)).daysElapsed(LocalDate.of(2026, 1, 1)))
        assertEquals(
            "a stretch longer than a short month stops at its end",
            LocalDate.of(2026, 3, 1),
            ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 2)).firstDays(31)?.untilExclusive,
        )
    }
}
