package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportCsvTest {

    private val header = listOf("Раздел", "Название", "Сумма")

    private val BOM = "\uFEFF"

    private fun build(vararg lines: ReportCsv.Line) =
        ReportCsv.build(header, lines.toList(), ReportCsv.Dialect.COMMA_DECIMAL)

    private val headings = ReportCsv.Headings(
        total = "Доход", period = "Июнь 2026", byGroup = "По группам", bySubject = "По предметам",
        byStudent = "По ученикам", debts = "Долги", outstanding = "Всего к получению",
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
    fun `an empty report is still a well-formed file with its header`() {
        assertEquals(BOM + "Раздел;Название;Сумма\r\n", build())
    }
}
