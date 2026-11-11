package com.seblful.klas.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** A CLDR date pattern as fields ("MMMM", "d") and the literal text between them. */
internal class DatePattern(val pieces: List<Piece>) {

    sealed interface Piece {
        data class Field(val letter: Char, val width: Int) : Piece {
            private val isMonth: Boolean get() = letter in "ML"
            val isShortMonth: Boolean get() = isMonth && width == SHORT_TEXT
            val isFullMonth: Boolean get() = isMonth && width > SHORT_TEXT
        }
        data class Text(val text: String) : Piece
    }

    private val fields: List<Int> = pieces.indices.filter { pieces[it] is Piece.Field }

    val startsWithDay: Boolean get() = pieces.firstOrNull().isDay()
    val endsWithDay: Boolean get() = pieces.lastOrNull().isDay()

    /** True when the pattern writes an hour on a 12-hour clock with its marker: "h:mm a", «a h:mm». */
    val isTwelveHour: Boolean
        get() = pieces.any { it is Piece.Field && it.letter == 'a' } &&
            pieces.any { it is Piece.Field && (it.letter == 'h' || it.letter == 'K') }

    /**
     * The pattern with its year and the text tying it in removed, wherever the year sits: "MMMM d, y" →
     * "MMMM d", «y年M月d日» → «M月d日», "d MMMM y EEEE" → "d MMMM EEEE".
     */
    fun withoutYear(): DatePattern {
        val year = fields.firstOrNull { (pieces[it] as Piece.Field).letter == 'y' } ?: return this
        val before = fields.lastOrNull { it < year }
        val after = fields.firstOrNull { it > year }
        return DatePattern(
            when {
                before == null -> pieces.drop(after ?: pieces.size)
                after == null -> pieces.take(before + 1)
                else -> pieces.take(before + 1) + pieces.drop(year + 1)
            },
        )
    }

    /** A full month name shortened, "MMMM" → "MMM"; a numeric month («M月») stays numeric. */
    fun withShortMonth(): DatePattern = DatePattern(
        pieces.map { if (it is Piece.Field && it.isFullMonth) it.copy(width = SHORT_TEXT) else it },
    )

    /** Drops text after a final year, «d MMMM y 'г'.» → «d MMMM y»; text after any other field stays («日»). */
    fun withoutTrailingText(): DatePattern {
        val last = fields.lastOrNull() ?: return this
        return if ((pieces[last] as Piece.Field).letter == 'y') DatePattern(pieces.take(last + 1)) else this
    }

    /**
     * [date] and [time] written out in [data]'s words, with Latin digits as `java.time` writes them. A month
     * spelled out is the date form (the genitive «октября»), whichever letter the pattern uses. A field the
     * app's patterns never contain writes nothing rather than failing a screen.
     */
    fun format(data: LocaleData, date: LocalDate? = null, time: LocalTime? = null): String = buildString {
        for (piece in pieces) {
            when (piece) {
                is Piece.Text -> append(piece.text)
                is Piece.Field -> append(piece.text(data, date, time))
            }
        }
    }

    private fun Piece?.isDay(): Boolean = this is Piece.Field && letter == 'd'

    companion object {
        /** A text field's short width, "MMM"; wider is the full name, narrower a number. */
        const val SHORT_TEXT = 3

        /** A text field's full width, "EEEE"; wider is the narrow form. */
        private const val FULL_TEXT = 4

        private const val HOURS_PER_HALF_DAY = 12
        private const val CENTURY = 100

        fun parse(pattern: String): DatePattern {
            val pieces = mutableListOf<Piece>()
            val text = StringBuilder()
            fun flushText() {
                if (text.isNotEmpty()) pieces += Piece.Text(text.toString())
                text.clear()
            }
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\'' -> {
                        val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                        if (end == i + 1) text.append('\'') else text.append(pattern, i + 1, end)
                        i = end + 1
                    }
                    c in 'a'..'z' || c in 'A'..'Z' -> {
                        flushText()
                        var j = i
                        while (j < pattern.length && pattern[j] == c) j++
                        pieces += Piece.Field(c, j - i)
                        i = j
                    }
                    else -> {
                        text.append(c)
                        i++
                    }
                }
            }
            flushText()
            return DatePattern(pieces)
        }

        @Suppress("CyclomaticComplexMethod")
        private fun Piece.Field.text(data: LocaleData, date: LocalDate?, time: LocalTime?): String {
            val weekday = date?.dayOfWeek?.ordinal
            val hour = time?.hour
            return when {
                letter == 'd' && date != null -> number(date.day)
                (letter == 'M' || letter == 'L') && date != null -> when {
                    isFullMonth -> data.months[date.month.ordinal]
                    isShortMonth -> data.shortMonths[date.month.ordinal]
                    else -> number(date.month.ordinal + 1)
                }
                letter == 'y' && date != null -> if (width == 2) number(date.year % CENTURY) else number(date.year)
                letter == 'E' && weekday != null ->
                    weekdayText(data.weekdays, data.shortWeekdays, data.narrowWeekdays)[weekday]
                letter == 'c' && weekday != null && width >= SHORT_TEXT -> weekdayText(
                    data.standaloneWeekdays,
                    data.standaloneShortWeekdays,
                    data.standaloneNarrowWeekdays,
                )[weekday]
                letter == 'G' -> data.era
                letter == 'a' && hour != null -> data.amPm[if (hour < HOURS_PER_HALF_DAY) 0 else 1]
                letter == 'h' && hour != null -> number(
                    (hour % HOURS_PER_HALF_DAY).takeIf {
                        it != 0
                    } ?: HOURS_PER_HALF_DAY,
                )
                letter == 'K' && hour != null -> number(hour % HOURS_PER_HALF_DAY)
                letter == 'H' && hour != null -> number(hour)
                letter == 'k' && hour != null -> number(hour.takeIf { it != 0 } ?: (2 * HOURS_PER_HALF_DAY))
                letter == 'm' && time != null -> number(time.minute)
                else -> ""
            }
        }

        private fun Piece.Field.weekdayText(full: List<String>, short: List<String>, narrow: List<String>) = when {
            width < FULL_TEXT -> short
            width == FULL_TEXT -> full
            else -> narrow
        }

        private fun Piece.Field.number(value: Int): String = value.toString().padStart(width, '0')
    }
}
