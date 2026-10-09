package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money

/**
 * The visible report as a spreadsheet.
 *
 * The app could already write a **backup**, and that is a different thing for a different purpose: it
 * is JSON, it is the whole database, and it exists to be read back by this app. What it cannot do is
 * be opened — by a tutor totalling their year, or by an accountant. Those two exports look alike from
 * a distance and share nothing but the file picker.
 *
 * Pure, and separate from the screen, so the format is testable without a `Uri`, a `Context` or a
 * composable — the same rule the rest of the domain follows.
 *
 * **Two decisions the format turns on, both about Excel:**
 *
 * - **The delimiter follows the decimal separator** ([Dialect]). Excel's list separator is `;` in every
 *   locale that writes `1500,50` and `,` in every locale that writes `1500.50`, and it silently mangles a
 *   file that uses the other one into a single column. So the two travel together: `;` and `,` for a
 *   Russian reader, `,` and `.` for an English one. This was a fixed pair of constants while the app had one
 *   language, with the Russian argument written out beside them.
 * - **A byte-order mark.** Excel reads a BOM-less file as the system's legacy 8-bit code page, which
 *   turns every Cyrillic label into mojibake — the one failure that makes the export worthless while
 *   looking like the app's fault. Anything else that reads CSV tolerates the BOM.
 *
 * Both are about the *reader*, which is why this is one of the places that does **not** follow the "read on
 * the phone or away from it" rule the clock does: a report is read away from the phone, but it is read by
 * the tutor who exported it, in a spreadsheet set up for the numbers they write.
 */
object ReportCsv {

    /**
     * How a spreadsheet in one language expects the file punctuated — the decimal separator, and the field
     * delimiter that must go with it.
     *
     * One type rather than two parameters, because choosing them independently is exactly the mistake:
     * `,`-delimited and `,`-decimal is a file no spreadsheet can read, and it is the combination a caller
     * threading two characters through would eventually produce.
     */
    enum class Dialect(val decimal: Char, val delimiter: Char) {
        /** `1500,50` in `a;b;c` — Russian, and every other comma-decimal locale. */
        COMMA_DECIMAL(',', ';'),

        /** `1500.50` in `a,b,c` — English. */
        DOT_DECIMAL('.', ',');

        companion object {
            /** The dialect for a reader who writes decimals with [decimal] — see `format/AppLocale`. */
            fun forDecimal(decimal: Char): Dialect =
                if (decimal == DOT_DECIMAL.decimal) DOT_DECIMAL else COMMA_DECIMAL
        }
    }

    /** One row: which section of the report it came from, what it is, and how much. */
    data class Line(val section: String, val label: String, val amount: Money)

    /**
     * The section headings, already localized — the domain does not own language, so the words come in
     * and only the *choice of rows* is decided here.
     *
     * @param period the name of the period the total covers, e.g. «Июнь 2026».
     */
    data class Headings(
        val total: String,
        val period: String,
        val byGroup: String,
        val bySubject: String,
        val byStudent: String,
        val debts: String,
        val outstanding: String,
    )

    /**
     * Which rows a report exports, in the order the screen shows them, so the file reads like the
     * report rather than like a table dump.
     *
     * **Debts are included even though they ignore the period.** The file is what the tutor takes away,
     * and leaving out the one section that says who owes money would make it the least useful export
     * possible; the heading says so, as the card on screen does. That, and the fact that the debts
     * section is skipped entirely when nobody owes anything rather than printing a bare total of zero,
     * is what the export *is* — and it was decided in a `@Composable`, so no test could open the file.
     */
    fun linesFor(report: Reports.Report, headings: Headings): List<Line> = buildList {
        add(Line(headings.total, headings.period, report.totalIncome))
        fun section(title: String, lines: List<Reports.Line>) {
            lines.forEach { add(Line(title, it.label, it.amount)) }
        }
        section(headings.byGroup, report.byGroup)
        section(headings.bySubject, report.bySubject)
        section(headings.byStudent, report.byStudent)
        if (report.debts.isNotEmpty()) {
            add(Line(headings.debts, headings.outstanding, report.totalOutstanding))
            section(headings.debts, report.debts)
        }
    }

    private const val NEWLINE = "\r\n"

    /**
     * Excel's cue that the file is UTF-8. See the class note.
     *
     * Written as an escape rather than as the character itself: a literal U+FEFF sitting inside a
     * source file is invisible in every editor and is exactly what lint's `ByteOrderMark` check
     * exists to catch — the point here is to *emit* one, not to contain one.
     */
    private const val BOM = "\uFEFF"

    /**
     * The whole file.
     *
     * @param header the three column names, already localized — the domain does not own language.
     * @param lines the rows, in the order the screen shows them, so the file reads like the report
     *   rather than like a table dump.
     * @param dialect how the spreadsheet that will open it punctuates numbers and fields. No default: the
     *   app's answer is its language's, and a default is how the wrong one would get picked everywhere.
     */
    fun build(header: List<String>, lines: List<Line>, dialect: Dialect): String = buildString {
        append(BOM)
        append(header.joinToString(dialect.delimiter.toString()) { escape(it, dialect) })
        append(NEWLINE)
        lines.forEach { line ->
            append(escape(line.section, dialect))
            append(dialect.delimiter)
            append(escape(line.label, dialect))
            append(dialect.delimiter)
            append(escape(amountText(line.amount, dialect), dialect))
            append(NEWLINE)
        }
    }

    /**
     * The amount as a bare decimal — no currency code.
     *
     * A currency suffix would make every cell text, and a column of text cannot be summed, which is
     * the first thing anyone does to a column of money. The code belongs in the file name or the
     * header, not in fifty cells.
     *
     * Always two decimal places, unlike [Money.amountText] on screen, which drops `,00`: a column where
     * some cells have a fraction and some do not is still a column of numbers, but the export is what gets
     * summed and reformatted, and a consistent scale is what makes that predictable.
     */
    private fun amountText(amount: Money, dialect: Dialect): String {
        val sign = if (amount.minor < 0) "-" else ""
        val abs = kotlin.math.abs(amount.minor)
        return "$sign${abs / 100}${dialect.decimal}${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * RFC 4180 quoting: a field containing the separator, a quote or a line break is wrapped in
     * quotes, and its own quotes are doubled.
     *
     * Not theoretical — a student's name is free text, and a group called «5"А"» or a note pasted in
     * with a newline would otherwise silently shift every following column by one.
     */
    private fun escape(field: String, dialect: Dialect): String =
        if (field.any { it == dialect.delimiter || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
