package com.seblful.klas.domain

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ReportCsvTest {

    private val header = listOf("Раздел", "Название", "Сумма")

    /**
     * U+FEFF as an escape, never as a literal: a byte-order mark typed into a source file is
     * invisible in every editor, and lint's `ByteOrderMark` check rejects one on sight. Spelling it
     * here is also what makes the assertions below say *which* character they are about.
     */
    private val BOM = "\uFEFF"

    private fun build(vararg lines: ReportCsv.Line) = ReportCsv.build(header, lines.toList())

    private val headings = ReportCsv.Headings(
        total = "Доход", period = "Июнь 2026", byGroup = "По группам", bySubject = "По предметам",
        byStudent = "По ученикам", debts = "Долги", outstanding = "Всего к получению",
    )

    private fun line(amount: Long, label: String) = Reports.Line(label, Money(amount))

    /**
     * Which rows the file contains. This used to be worked out in `ReportsScreen`, so the one thing
     * anybody actually checks about an export — that the sections are all there and in screen order —
     * was outside the reach of every test.
     */
    @Test
    fun `the file is the total, then each section in screen order, debts last`() {
        val report = Reports.Report(
            period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 6)),
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

    /**
     * Nobody owing anything is not a debts section with a zero in it. The heading and its total both go,
     * because a line saying «Всего к получению; 0,00» reads as an assertion rather than as an absence.
     */
    @Test
    fun `a report nobody owes anything on has no debts section at all`() {
        val report = Reports.Report(
            period = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2026, 6)),
            totalIncome = Money(9000),
            byStudent = listOf(line(9000, "Аня")),
        )

        assertEquals(
            listOf("Доход", "По ученикам"),
            ReportCsv.linesFor(report, headings).map { it.section },
        )
    }

    /** Excel reads a BOM-less UTF-8 file as a legacy code page, which turns Cyrillic into mojibake. */
    @Test
    fun `the file starts with a byte-order mark`() {
        assertTrue(build().startsWith(BOM))
    }

    @Test
    fun `rows are semicolon-separated, so a comma stays free for the decimals`() {
        val csv = build(ReportCsv.Line("Доход", "Аня", Money.ofMajor(30)))

        assertEquals(BOM + "Раздел;Название;Сумма\r\nДоход;Аня;30,00\r\n", csv)
    }

    /** A bare decimal: a currency suffix would make every cell text, and text cannot be summed. */
    @Test
    fun `amounts carry no currency code and always show two places`() {
        val csv = build(
            ReportCsv.Line("s", "round", Money.ofMajor(30)),
            ReportCsv.Line("s", "kopecks", Money(125_050)),
            ReportCsv.Line("s", "small", Money(5)),
            ReportCsv.Line("s", "zero", Money.ZERO),
        )

        assertTrue(csv, csv.contains("s;round;30,00"))
        assertTrue(csv, csv.contains("s;kopecks;1250,50"))
        assertTrue(csv, csv.contains("s;small;0,05"))
        assertTrue(csv, csv.contains("s;zero;0,00"))
    }

    @Test
    fun `a negative amount keeps its sign`() {
        val csv = build(ReportCsv.Line("s", "credit", Money.ZERO - Money.ofMajor(20)))

        assertTrue(csv, csv.contains("s;credit;-20,00"))
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

        assertTrue(csv, csv.contains("\"Группа; вечерняя\""))
        assertTrue(csv, csv.contains("\"5\"\"А\"\"\""))
    }

    @Test
    fun `a field containing a newline is quoted rather than breaking the row`() {
        val csv = build(ReportCsv.Line("Доход", "Аня\nПетрова", Money.ofMajor(10)))

        assertTrue(csv, csv.contains("\"Аня\nПетрова\""))
        // Header, one data row, and nothing else: the embedded newline did not start a second row.
        assertEquals(2, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `an empty report is still a well-formed file with its header`() {
        assertEquals(BOM + "Раздел;Название;Сумма\r\n", build())
    }
}
