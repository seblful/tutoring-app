package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.PaymentMethod
import kotlinx.datetime.number

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
     * What a shared report is **called**: `klas-report-2026-06.csv`, `klas-report-2026.csv`,
     * `klas-report-all.csv`.
     *
     * The period is in the name because the file leaves the app. Every share was `klas-report.csv`, so an
     * accountant sent twelve months received twelve files with one name and no way to tell them apart — and
     * the subject line that does name the period belongs to the message rather than to the attachment.
     *
     * Not localized: it is a filename, read by whatever the receiving app is, and `klas-report-2026-06`
     * sorts and is typed the same everywhere. The screen's own «Выгрузить в CSV» dialog keeps its plain
     * `reports_export_filename`, since the tutor is naming that one themselves.
     */
    fun fileName(period: ReportPeriod): String {
        val suffix = when (period.kind) {
            ReportPeriod.Kind.MONTH -> "${period.anchor.year}-${period.anchor.month.number.toString().padStart(2, '0')}"
            ReportPeriod.Kind.YEAR -> period.anchor.year.toString()
            ReportPeriod.Kind.ALL -> "all"
        }
        return "klas-report-$suffix.csv"
    }

    /** What a shared ledger is called: [fileName]'s period, `klas-ledger-2026-06.csv`. */
    fun ledgerFileName(period: ReportPeriod): String = fileName(period).replace("klas-report", "klas-ledger")

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
        DOT_DECIMAL('.', ','),
        ;

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
        /** «Получено» — the [Reports.Received] section; [methods] names its rows, null for "not said". */
        val received: String,
        val methods: Map<PaymentMethod?, String>,
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
        // Money in beside money earned — its own section, as on screen (D6), and only when there was any.
        val received = report.received
        if (!received.total.isZero) {
            add(Line(headings.received, headings.period, received.total))
            if (received.showsMethods) {
                received.byMethod.forEach {
                    add(Line(headings.received, headings.methods[it.method].orEmpty(), it.amount))
                }
            }
        }
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

    /** The ledger's words, already localized: what a lesson row and a payment row are, and each method's name. */
    data class LedgerWords(val lesson: String, val payment: String, val methods: Map<PaymentMethod?, String>)

    /**
     * The ledger: every taught lesson and every payment in the report's period, one row each, by day — what
     * an accountant reconciles, where the summary ([build]) is what a tutor reads. The day is ISO
     * (`2026-06-14`), which every spreadsheet sorts and reads as a date whatever its language.
     *
     * @param header the five column names — day, entry, student, subject or method, amount — localized.
     */
    fun buildLedger(header: List<String>, report: Reports.Report, words: LedgerWords, dialect: Dialect): String =
        buildString {
            append(BOM)
            append(header.joinToString(dialect.delimiter.toString()) { escape(it, dialect) })
            append(NEWLINE)
            report.ledger.forEach { entry ->
                val fields = listOf(
                    entry.day.toString(),
                    if (entry.payment) words.payment else words.lesson,
                    entry.student,
                    if (entry.payment) words.methods[entry.method].orEmpty() else entry.subject.orEmpty(),
                    amountText(entry.amount, dialect),
                )
                append(fields.joinToString(dialect.delimiter.toString()) { escape(it, dialect) })
                append(NEWLINE)
            }
        }

    private const val NEWLINE = "\r\n"

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

    private fun amountText(amount: Money, dialect: Dialect): String {
        val sign = if (amount.minor < 0) "-" else ""
        val abs = kotlin.math.abs(amount.minor)
        return "$sign${abs / 100}${dialect.decimal}${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun escape(field: String, dialect: Dialect): String =
        if (field.any { it == dialect.delimiter || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
