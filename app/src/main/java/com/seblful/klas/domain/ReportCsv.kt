package com.seblful.klas.domain

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
 * **Two decisions the format turns on, both about Excel and Cyrillic:**
 *
 * - **Semicolons, not commas.** The audience is Russian-speaking, and Excel in a locale whose decimal
 *   separator is the comma parses `;`-separated files and mangles `,`-separated ones into a single
 *   column. It also frees the comma to be what a Russian reader expects inside a number.
 * - **A byte-order mark.** Excel reads a BOM-less file as the system's legacy 8-bit code page, which
 *   turns every Cyrillic label into mojibake — the one failure that makes the export worthless while
 *   looking like the app's fault. Anything else that reads CSV tolerates the BOM.
 */
object ReportCsv {

    /** One row: which section of the report it came from, what it is, and how much. */
    data class Line(val section: String, val label: String, val amount: Money)

    private const val SEPARATOR = ';'
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
     */
    fun build(header: List<String>, lines: List<Line>): String = buildString {
        append(BOM)
        append(header.joinToString(SEPARATOR.toString()) { escape(it) })
        append(NEWLINE)
        lines.forEach { line ->
            append(escape(line.section))
            append(SEPARATOR)
            append(escape(line.label))
            append(SEPARATOR)
            append(escape(amountText(line.amount)))
            append(NEWLINE)
        }
    }

    /**
     * The amount as a bare decimal with a comma — no currency code.
     *
     * A currency suffix would make every cell text, and a column of text cannot be summed, which is
     * the first thing anyone does to a column of money. The code belongs in the file name or the
     * header, not in fifty cells.
     */
    private fun amountText(amount: Money): String {
        val sign = if (amount.minor < 0) "-" else ""
        val abs = kotlin.math.abs(amount.minor)
        return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * RFC 4180 quoting: a field containing the separator, a quote or a line break is wrapped in
     * quotes, and its own quotes are doubled.
     *
     * Not theoretical — a student's name is free text, and a group called «5"А"» or a note pasted in
     * with a newline would otherwise silently shift every following column by one.
     */
    private fun escape(field: String): String =
        if (field.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
