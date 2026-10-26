package com.seblful.klas.domain.reports

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.StudentBalance
import com.seblful.klas.domain.model.StudentId
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportsTest {

    private val zone = TimeZone.of("Europe/Minsk")

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

    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    private fun reportOf(
        classes: List<TutoringClass>,
        period: ReportPeriod,
        today: LocalDate = LocalDate(2100, 1, 1),
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

    @Test
    fun `one kind of teaching is not a section`() {
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 5))
        val individualOnly = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T10:00:00Z", 3000),
        )

        assertEquals(
            emptyList<Reports.KindLine>(),
            reportOf(individualOnly, period).byKind,
            "a heading over one line saying what the total already says",
        )

        val bothKinds = individualOnly + klass(3, 10, "2026-05-12T10:00:00Z", 2000, groupId = 7)
        val split = reportOf(bothKinds, period).byKind
        assertEquals(2, split.size, "both kinds earned, so the split is worth showing")
        assertEquals(LessonKind.INDIVIDUAL, split.first().kind, "largest first")
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
        assertEquals(2026 * 12 + 5, byMonth[0].monthKey) // June (monthValue 6 -> index 5)
        assertEquals(Money(4000), byMonth[0].total)
        assertEquals(2026 * 12 + 4, byMonth[1].monthKey) // May
        assertEquals(Money(8000), byMonth[1].total)
    }

    @Test
    fun `moving a completed lesson forward takes its income out of the month - without relocating it`() {
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
            emptyList<Reports.MonthlyIncome>(),
            Reports.incomeByMonth(listOf(moved), zone),
            "neither May nor June recognizes a lesson that has not happened yet",
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
    fun `income by kind splits one-to-one from group teaching - largest first`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(3, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(4, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(1, 10, "2026-05-12T10:00:00Z", 5000, LessonStatus.CANCELLED),
            klass(2, 10, "2026-05-12T18:00:00Z", 2000, LessonStatus.SCHEDULED, groupId = 7),
        )

        val lines = Reports.incomeByKind(classes)

        assertEquals(listOf(LessonKind.GROUP, LessonKind.INDIVIDUAL), lines.map { it.kind })
        assertEquals(Money(6000), lines[0].total)
        assertEquals(Money(5000), lines[1].total)
        assertEquals(Reports.totalIncome(classes), lines.fold(Money.ZERO) { a, l -> a + l.total })
    }

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
    fun `income by group counts only group lessons - keyed by the group`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000), // individual: not in the breakdown at all
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(3, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(4, 10, "2026-05-12T18:00:00Z", 3000, groupId = 8),
            klass(5, 10, "2026-05-13T18:00:00Z", 9000, LessonStatus.CANCELLED, groupId = 8),
        )

        val byGroup = Reports.incomeByGroup(classes)

        assertEquals(Money(4000), byGroup[GroupId(7)])
        assertEquals(Money(3000), byGroup[GroupId(8)])
        assertEquals(
            setOf(GroupId(7), GroupId(8)),
            byGroup.keys,
            "individual lessons have no group to be counted under",
        )
    }

    @Test
    fun `a percentage is rounded half-up and needs no whole to be safe`() {
        assertEquals(33, Reports.percentOf(Money(1000), Money(3000)))
        assertEquals(67, Reports.percentOf(Money(2000), Money(3000)))
        assertEquals(50, Reports.percentOf(Money(50), Money(100)))
        assertEquals(100, Reports.percentOf(Money(4000), Money(4000)))
        assertEquals(0, Reports.percentOf(Money(0), Money(4000)))
        assertEquals(0, Reports.percentOf(Money(4000), Money.ZERO))
    }

    @Test
    fun `three equal shares each report a third - not a forced hundred`() {
        val third = Reports.percentOf(Money(1000), Money(3000))
        assertEquals(99, third * 3)
    }

    @Test
    fun `a year's trend is its twelve months - oldest first - quiet ones included`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 5000),
            klass(1, 10, "2026-02-20T10:00:00Z", 1000),
            klass(1, 10, "2026-11-10T10:00:00Z", 3000),
            klass(1, 10, "2025-06-10T10:00:00Z", 9900), // another year: outside the period
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals(Reports.Trend.Bucket.MONTHS, trend.bucket)
        assertEquals(12, trend.bars.size, "a year is drawn as all twelve of its months")
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
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals(12, trend.bars.size, "twelve bars in the chart")
        assertEquals(
            listOf(keyOf(2026, 11) to Money(3000), keyOf(2026, 2) to Money(5000)),
            trend.rows.map { it.key to it.total },
            "two rows in the list, November before February",
        )
    }

    @Test
    fun `all time is drawn as years - from the first that earned to the last`() {
        val classes = listOf(
            klass(1, 10, "2024-03-10T10:00:00Z", 1000),
            klass(1, 10, "2026-03-10T10:00:00Z", 4000),
        )
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 7)), zone)

        assertEquals(Reports.Trend.Bucket.YEARS, trend.bucket)
        assertEquals(listOf(2024, 2025, 2026), trend.bars.map { it.key })
        assertEquals(listOf(Money(1000), Money.ZERO, Money(4000)), trend.bars.map { it.total })
        assertEquals(2026, trend.peak?.key)
    }

    @Test
    fun `a single month has nothing to compare itself with`() {
        val classes = listOf(klass(1, 10, "2026-05-10T10:00:00Z", 5000))
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 5)), zone)

        assertEquals(1, trend.bars.size)
        assertTrue(trend.isEmpty, "one bar is already said by the total above it")
    }

    @Test
    fun `a period that earned nothing has no chart - however many buckets it spans`() {
        val classes = listOf(klass(1, 10, "2026-05-10T10:00:00Z", 5000, LessonStatus.CANCELLED))
        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 5)), zone)

        assertEquals(12, trend.bars.size)
        assertNull(trend.peak)
        assertTrue(trend.isEmpty, "twelve empty bars are not a chart")
        assertTrue(Reports.trend(emptyList(), ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 5)), zone).isEmpty)
    }

    @Test
    fun `the trend adds up to the period's income`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 5000),
            klass(2, 10, "2026-11-11T18:00:00Z", 2000, groupId = 7),
            klass(1, 10, "2025-02-10T10:00:00Z", 8000), // last year: in neither
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 1))
        val trend = Reports.trend(classes, period, zone)

        assertEquals(
            Reports.totalIncome(period.filter(classes, zone)),
            trend.bars.fold(Money.ZERO) { a, b -> a + b.total },
        )
    }

    @Test
    fun `debts lists only students who owe - largest first`() {
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

    @Test
    fun `what is outstanding carries who owes it, most first`() {
        val balances = listOf(
            StudentBalance(1, charged = Money(5000), paid = Money(2000)), // owes 3000
            StudentBalance(2, charged = Money(4000), paid = Money(9000)), // credit
            StudentBalance(3, charged = Money(10000), paid = Money(1000)), // owes 9000
        )

        val outstanding = Reports.Outstanding.of(balances)

        assertEquals(2, outstanding.students)
        assertEquals(Money(12000), outstanding.total)
        assertEquals(listOf(StudentId(3), StudentId(1)), outstanding.debts.map { it.student })
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
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))

        val report = reportOf(classes, period)

        assertEquals(listOf(1.0f, 0.25f), report.bySubject.map { it.share })
    }

    /** What a list keys a line by, so a row is followed — not matched by position — as the ranking moves. */
    @Test
    fun `each line is identified by the student - subject or group it is about`() {
        val classes = listOf(
            klass(1, 10, "2026-06-10T10:00:00Z", 4000, groupId = 7),
            klass(2, 20, "2026-06-11T10:00:00Z", 1000),
        )
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))

        val report = reportOf(classes, period)

        assertEquals(listOf(1L, 2L), report.byStudent.map { it.id })
        assertEquals(listOf(10L, 20L), report.bySubject.map { it.id })
        assertEquals(listOf(7L), report.byGroup.map { it.id })
        assertEquals(listOf(1L, 2L), report.debts.map { it.id })
    }

    @Test
    fun `a section of one line offers no bar — nothing to rank it against`() {
        val classes = listOf(klass(1, 10, "2026-06-10T10:00:00Z", 4000))
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))

        val report = reportOf(classes, period)

        assertNull(report.bySubject.single().share)
    }

    @Test
    fun `a bar's key encodes its month, which the module unpacks`() {
        val classes = listOf(
            klass(1, 10, "2026-02-10T10:00:00Z", 6000),
            klass(1, 10, "2026-11-10T10:00:00Z", 3000),
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 7))

        val trend = Reports.trend(classes, period, zone)

        assertEquals(1, trend.bars[1].monthIndex, "the key's encoding is the module's to unpack")
        assertEquals(10, trend.bars[10].monthIndex)
    }

    @Test
    fun `a year in progress is compared with the same days of the year before`() {
        val classes = listOf(
            klass(1, 10, "2025-03-10T10:00:00Z", 10000), // inside last year's first 267 days
            klass(1, 10, "2025-11-10T10:00:00Z", 90000), // after them: not this year's to answer for yet
            klass(1, 10, "2026-03-10T10:00:00Z", 12000),
        )
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 9))

        val report = reportOf(classes, year, today = LocalDate(2026, 9, 24))

        assertEquals(Reports.Change.Percent(20), report.change)
        assertTrue(report.changeToDate, "and it says it measured the days so far")
    }

    @Test
    fun `a finished year is compared whole`() {
        val classes = listOf(
            klass(1, 10, "2025-03-10T10:00:00Z", 10000),
            klass(1, 10, "2025-11-10T10:00:00Z", 10000),
            klass(1, 10, "2026-03-10T10:00:00Z", 30000),
        )
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 1))

        val report = reportOf(classes, year, today = LocalDate(2027, 2, 1))

        assertEquals(Reports.Change.Percent(50), report.change)
        assertEquals(false, report.changeToDate)
    }

    @Test
    fun `a month in progress is compared with the same days of the month before`() {
        val classes = listOf(
            klass(1, 10, "2026-08-05T10:00:00Z", 4000),
            klass(1, 10, "2026-08-28T10:00:00Z", 4000),
            klass(1, 10, "2026-09-05T10:00:00Z", 6000),
        )
        val month = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 9))

        val report = reportOf(classes, month, today = LocalDate(2026, 9, 24))

        assertEquals(Reports.Change.Percent(50), report.change)
    }

    @Test
    fun `the days a period has had are counted from its first - and none once it is over`() {
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 1))

        assertEquals(1L, year.daysElapsed(LocalDate(2026, 1, 1)))
        assertEquals(267L, year.daysElapsed(LocalDate(2026, 9, 24)))
        assertNull(year.daysElapsed(LocalDate(2027, 1, 1)), "over")
        assertNull(year.daysElapsed(LocalDate(2025, 12, 31)), "not begun")
        assertNull(
            ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 1)).daysElapsed(LocalDate(2026, 1, 1)),
            "all time has no days",
        )
        assertEquals(
            LocalDate(2026, 3, 1),
            ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 2)).firstDays(31)?.untilExclusive,
            "a stretch longer than a short month stops at its end",
        )
    }

    @Test
    fun `a month's window reaches back to the month it is compared with`() {
        assertEquals(
            ReportPeriod.Bounds(LocalDate(2026, 5, 1), LocalDate(2026, 7, 1)),
            Reports.window(ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))),
        )
    }

    @Test
    fun `all time has no window`() {
        assertNull(Reports.window(ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 6))))
    }

    @Test
    fun `a report from its window and summed balances matches one from every class`() {
        val classes = listOf(
            klass(1, 1, "2024-03-10T10:00:00Z", 3_000),
            klass(1, 1, "2025-04-10T10:00:00Z", 3_000),
            klass(2, 1, "2026-02-10T10:00:00Z", 4_500),
            klass(2, 2, "2026-06-10T10:00:00Z", 4_500, status = LessonStatus.SCHEDULED),
        )
        val students = listOf(Student(id = 1, name = "S1"), Student(id = 2, name = "S2"))
        val payments = listOf(
            Payment(studentId = 1, date = Instant.parse("2025-05-01T00:00:00Z"), amount = Money(1_000)),
        )
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 6))
        val today = LocalDate(2026, 6, 20)
        val subjectNames = mapOf(SubjectId(1) to "П1", SubjectId(2) to "П2")
        val window = assertNotNull(Reports.window(period))

        val whole = Reports.of(period, classes, students, payments, subjectNames, emptyMap(), zone, today)
        val windowed = Reports.of(
            period = period,
            classes = window.filter(classes, zone),
            students = students,
            balances = Billing.balancesFor(students, classes, payments),
            hasData = true,
            subjectNames = subjectNames,
            groupNames = emptyMap(),
            zone = zone,
            today = today,
        )

        assertEquals(whole, windowed)
    }
}
