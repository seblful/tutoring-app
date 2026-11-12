package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportCsvTest {

    private val header = listOf("Раздел", "Название", "Сумма")

    private val BOM = "\uFEFF"

    private fun build(vararg lines: ReportCsv.Line) =
        ReportCsv.build(header, lines.toList(), ReportCsv.Dialect.COMMA_DECIMAL)

    private val headings = ReportCsv.Headings(
        total = "Доход", period = "Июнь 2026", byGroup = "По группам", bySubject = "По предметам",
        byStudent = "По ученикам", debts = "Долги", outstanding = "Всего к получению",
        received = "Получено",
        methods = mapOf(
            PaymentMethod.CASH to "Наличные", PaymentMethod.CARD to "Карта",
            PaymentMethod.TRANSFER to "Перевод", null to "Не указано",
        ),
    )

    private fun line(amount: Long, label: String) = Reports.Line(id = 0, label = label, amount = Money(amount))

    @Test
    fun `a shared file is named after its period`() {
        val june = YearMonth(2026, 6)
        assertEquals("klas-report-2026-06.csv", ReportCsv.fileName(ReportPeriod(ReportPeriod.Kind.MONTH, june)))
        assertEquals("klas-report-2026.csv", ReportCsv.fileName(ReportPeriod(ReportPeriod.Kind.YEAR, june)))
        assertEquals("klas-report-all.csv", ReportCsv.fileName(ReportPeriod(ReportPeriod.Kind.ALL, june)))
    }

    @Test
    fun `the file is the total - then each section in screen order - debts last`() {
        val report = Reports.Report(
            period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6)),
            totalIncome = Money(9000),
            byGroup = listOf(line(2000, "11 «А»")),
            bySubject = listOf(line(6000, "Алгебра"), line(3000, "Физика")),
            byStudent = listOf(line(5000, "Аня")),
            debts = listOf(line(1500, "Борис")),
            totalOutstanding = Money(1500),
        )

        assertEquals(
            listOf(
                "Доход" to "Июнь 2026",
                "По группам" to "11 «А»",
                "По предметам" to "Алгебра",
                "По предметам" to "Физика",
                "По ученикам" to "Аня",
                "Долги" to "Всего к получению",
                "Долги" to "Борис",
            ),
            ReportCsv.linesFor(report, headings).map { it.section to it.label },
        )
    }

    @Test
    fun `money received follows the income split by method when any payment said one`() {
        val report = Reports.Report(
            period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6)),
            totalIncome = Money(9000),
            received = Reports.Received(
                Money(7000),
                listOf(Reports.MethodLine(PaymentMethod.CARD, Money(5000)), Reports.MethodLine(null, Money(2000))),
            ),
        )

        assertEquals(
            listOf(
                "Доход" to "Июнь 2026",
                "Получено" to "Июнь 2026",
                "Получено" to "Карта",
                "Получено" to "Не указано",
            ),
            ReportCsv.linesFor(report, headings).map { it.section to it.label },
        )
    }

    @Test
    fun `a period nothing was paid in has no received section and unsaid methods no split`() {
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))
        val unpaid = Reports.Report(period = period, totalIncome = Money(9000))
        val unsaid = unpaid.copy(
            received = Reports.Received(Money(3000), listOf(Reports.MethodLine(null, Money(3000)))),
        )

        assertEquals(listOf("Доход"), ReportCsv.linesFor(unpaid, headings).map { it.section })
        assertEquals(listOf("Доход", "Получено"), ReportCsv.linesFor(unsaid, headings).map { it.section })
    }

    @Test
    fun `a report nobody owes anything on has no debts section at all`() {
        val report = Reports.Report(
            period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6)),
            totalIncome = Money(9000),
            byStudent = listOf(line(9000, "Аня")),
        )

        assertEquals(
            listOf("Доход", "По ученикам"),
            ReportCsv.linesFor(report, headings).map { it.section },
        )
    }

    @Test
    fun `the file starts with a byte-order mark`() {
        assertTrue(build().startsWith(BOM))
    }

    @Test
    fun `rows are semicolon-separated - so a comma stays free for the decimals`() {
        val csv = build(ReportCsv.Line("Доход", "Аня", Money.ofMajor(30)))

        assertEquals(BOM + "Раздел;Название;Сумма\r\nДоход;Аня;30,00\r\n", csv)
    }

    @Test
    fun `amounts carry no currency code and always show two places`() {
        val csv = build(
            ReportCsv.Line("s", "round", Money.ofMajor(30)),
            ReportCsv.Line("s", "kopecks", Money(125_050)),
            ReportCsv.Line("s", "small", Money(5)),
            ReportCsv.Line("s", "zero", Money.ZERO),
        )

        assertTrue(csv.contains("s;round;30,00"), csv)
        assertTrue(csv.contains("s;kopecks;1250,50"), csv)
        assertTrue(csv.contains("s;small;0,05"), csv)
        assertTrue(csv.contains("s;zero;0,00"), csv)
    }

    @Test
    fun `a negative amount keeps its sign`() {
        val csv = build(ReportCsv.Line("s", "credit", Money.ZERO - Money.ofMajor(20)))

        assertTrue(csv.contains("s;credit;-20,00"), csv)
    }

    @Test
    fun `an English dialect writes dot decimals in a comma-separated file`() {
        val csv = ReportCsv.build(
            listOf("Section", "Name", "Amount"),
            listOf(ReportCsv.Line("Income", "Anya", Money(125_050))),
            ReportCsv.Dialect.DOT_DECIMAL,
        )

        assertEquals(BOM + "Section,Name,Amount\r\nIncome,Anya,1250.50\r\n", csv)
    }

    @Test
    fun `the quoted character is the dialect's own delimiter`() {
        val commas = ReportCsv.build(
            header,
            listOf(ReportCsv.Line("Income", "Petrova, Anya", Money.ofMajor(10))),
            ReportCsv.Dialect.DOT_DECIMAL,
        )
        val semicolons = ReportCsv.build(
            header,
            listOf(ReportCsv.Line("Income", "Petrova, Anya", Money.ofMajor(10))),
            ReportCsv.Dialect.COMMA_DECIMAL,
        )

        assertTrue(commas.contains("\"Petrova, Anya\""), commas)
        assertTrue(semicolons.contains(";Petrova, Anya;"), "a comma is an ordinary character here")
    }

    @Test
    fun `a dialect is picked by the separator the reader writes decimals with`() {
        assertEquals(ReportCsv.Dialect.DOT_DECIMAL, ReportCsv.Dialect.forDecimal('.'))
        assertEquals(ReportCsv.Dialect.COMMA_DECIMAL, ReportCsv.Dialect.forDecimal(','))
        assertEquals(
            ReportCsv.Dialect.COMMA_DECIMAL,
            ReportCsv.Dialect.forDecimal('٫'),
            "anything else is a comma-decimal locale, which is the app's older half",
        )
    }

    /**
     * Names are free text. A group called «5"А"», or one with a semicolon in it, would otherwise
     * shift every following column by one — silently, in a file the tutor takes to an accountant.
     */
    @Test
    fun `a field containing the separator or a quote is quoted`() {
        val csv = build(
            ReportCsv.Line("Доход", "Группа; вечерняя", Money.ofMajor(10)),
            ReportCsv.Line("Доход", "5\"А\"", Money.ofMajor(10)),
        )

        assertTrue(csv.contains("\"Группа; вечерняя\""), csv)
        assertTrue(csv.contains("\"5\"\"А\"\"\""), csv)
    }

    @Test
    fun `a field containing a newline is quoted rather than breaking the row`() {
        val csv = build(ReportCsv.Line("Доход", "Аня\nПетрова", Money.ofMajor(10)))

        assertTrue(csv.contains("\"Аня\nПетрова\""), csv)
        assertEquals(2, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `a name a spreadsheet would run as a formula is written as text`() {
        val csv = build(
            ReportCsv.Line("Доход", "=HYPERLINK(\"http://evil\")", Money.ofMajor(10)),
            ReportCsv.Line("Доход", "+1", Money.ofMajor(10)),
            ReportCsv.Line("Доход", "-Аня", Money.ofMajor(10)),
            ReportCsv.Line("Доход", "@SUM(A1)", Money.ofMajor(10)),
        )

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), csv)
        assertTrue(csv.contains(";'+1;"), csv)
        assertTrue(csv.contains(";'-Аня;"), csv)
        assertTrue(csv.contains(";'@SUM(A1);"), csv)
    }

    @Test
    fun `a negative amount stays a number when names are made safe`() {
        val csv = build(ReportCsv.Line("Долги", "Аня", Money(-1250)))

        assertTrue(csv.contains("Долги;Аня;-12,50\r\n"), csv)
    }

    @Test
    fun `the ledger writes a formula-like student or subject as text and keeps its amount`() {
        val report = reportFromHistory(
            period = june,
            classes = listOf(taught(1, "2026-06-14T10:00:00Z", -3000)),
            students = listOf(Student(id = 1, name = "=cmd|' /C calc'!A0")),
            payments = emptyList(),
            subjectNames = mapOf(SubjectId(1) to "@Физика"),
            groupNames = emptyMap(),
            zone = TimeZone.UTC,
            today = LocalDate(2026, 6, 30),
        )
        val words = ReportCsv.LedgerWords(lesson = "Занятие", payment = "Оплата", methods = headings.methods)

        val csv = ReportCsv.buildLedger(listOf("Д", "З", "У", "П", "С"), report, words, ReportCsv.Dialect.COMMA_DECIMAL)

        assertTrue(csv.contains("2026-06-14;Занятие;'=cmd|' /C calc'!A0;'@Физика;-30,00\r\n"), csv)
    }

    @Test
    fun `an empty report is still a well-formed file with its header`() {
        assertEquals(BOM + "Раздел;Название;Сумма\r\n", build())
    }

    private val june = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))

    private fun taught(id: Long, at: String, charge: Long, status: LessonStatus = LessonStatus.COMPLETED) =
        TutoringClass(
            id = id, lessonId = id, studentId = 1, subjectId = 1,
            start = Instant.parse(at), durationMinutes = 60, price = Money(charge),
            status = status, attended = Attended.PRESENT, charge = Money(charge),
        )

    private fun paid(at: String, amount: Long, method: PaymentMethod? = null) =
        Payment(studentId = 1, date = Instant.parse(at), amount = Money(amount), method = method)

    private fun lesson(day: Int, amount: Long) =
        Reports.LedgerEntry(LocalDate(2026, 6, day), "Аня", "Физика", payment = false, method = null, Money(amount))

    private fun ledgerOf(classes: List<TutoringClass>, payments: List<Payment>) = reportFromHistory(
        period = june,
        classes = classes,
        students = listOf(Student(id = 1, name = "Аня")),
        payments = payments,
        subjectNames = mapOf(SubjectId(1) to "Физика"),
        groupNames = emptyMap(),
        zone = TimeZone.UTC,
        today = LocalDate(2026, 6, 30),
    )

    @Test
    fun `the ledger has every taught lesson and payment of the period by day - a lesson before a payment`() {
        val report = ledgerOf(
            classes = listOf(
                taught(1, "2026-06-14T10:00:00Z", 3000),
                taught(2, "2026-06-02T10:00:00Z", 3000),
                taught(3, "2026-06-20T10:00:00Z", 3000, LessonStatus.SCHEDULED),
                taught(4, "2026-05-30T10:00:00Z", 3000),
            ),
            payments = listOf(
                paid("2026-06-14T08:00:00Z", 6000, PaymentMethod.CASH),
                paid("2026-07-01T08:00:00Z", 1000),
            ),
        )

        assertEquals(
            listOf(
                lesson(day = 2, amount = 3000),
                lesson(day = 14, amount = 3000),
                Reports.LedgerEntry(LocalDate(2026, 6, 14), "Аня", null, true, PaymentMethod.CASH, Money(6000)),
            ),
            report.ledger,
        )
    }

    @Test
    fun `the ledger file is a row per entry - ISO day then kind then student then subject or method then amount`() {
        val report = ledgerOf(
            classes = listOf(taught(1, "2026-06-14T10:00:00Z", 3000)),
            payments = listOf(paid("2026-06-15T08:00:00Z", 6000)),
        )
        val words = ReportCsv.LedgerWords(lesson = "Занятие", payment = "Оплата", methods = headings.methods)

        val csv = ReportCsv.buildLedger(
            listOf("Дата", "Запись", "Ученик", "Предмет или способ", "Сумма"),
            report,
            words,
            ReportCsv.Dialect.COMMA_DECIMAL,
        )

        assertEquals(
            BOM + "Дата;Запись;Ученик;Предмет или способ;Сумма\r\n" +
                "2026-06-14;Занятие;Аня;Физика;30,00\r\n" +
                "2026-06-15;Оплата;Аня;Не указано;60,00\r\n",
            csv,
        )
        assertEquals("klas-ledger-2026-06.csv", ReportCsv.ledgerFileName(june))
    }
}
