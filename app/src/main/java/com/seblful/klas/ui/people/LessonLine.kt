package com.seblful.klas.ui.people

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.lessons.NearestLessons
import com.seblful.klas.format.formatNearDate
import com.seblful.klas.format.shortDisplayName
import com.seblful.klas.ui.common.timeText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * What a students or groups card says about lessons: the next one, or with none booked the last one — which
 * is what tells a tutor who has quietly stopped coming.
 */
sealed interface LessonLine {
    /** @param daysAway 0 today, 1 tomorrow; past a week the time is dropped for the date. */
    data class Next(val date: LocalDate, val time: LocalTime, val daysAway: Int, val thisYear: Int) : LessonLine

    data class Last(val date: LocalDate, val thisYear: Int) : LessonLine

    data object Never : LessonLine

    companion object {
        fun of(nearest: NearestLessons, now: Instant, zone: TimeZone): LessonLine {
            nearest.next?.let { return next(it, now, zone) }
            val today = now.toLocalDateTime(zone).date
            return nearest.last?.let { Last(it.toLocalDateTime(zone).date, today.year) } ?: Never
        }

        /** A lesson starting [start], said from [now] — the student card's next lesson as the list says it. */
        fun next(start: Instant, now: Instant, zone: TimeZone): Next {
            val today = now.toLocalDateTime(zone).date
            val at = start.toLocalDateTime(zone)
            return Next(at.date, at.time, today.daysUntil(at.date), today.year)
        }
    }
}

/**
 * @param timeAlways keep the time past a week too — the student card's next lesson, a line of its own where
 * the list's card has a fact line to share.
 */
@Composable
fun lessonLineText(line: LessonLine, timeAlways: Boolean = false): String = when (line) {
    is LessonLine.Next -> when (line.daysAway) {
        // A lesson under way started today; one that started yesterday and runs past midnight is today's too.
        in Int.MIN_VALUE..0 -> stringResource(R.string.people_next_today, line.time.timeText())
        1 -> stringResource(R.string.people_next_tomorrow, line.time.timeText())
        in 2 until DAYS_NAMED -> stringResource(
            R.string.people_next_weekday,
            line.date.dayOfWeek.shortDisplayName(),
            line.time.timeText(),
        )
        // «%1$s %2$s» is a day then a time, whichever way the day is named.
        else -> if (timeAlways) {
            stringResource(R.string.people_next_weekday, line.date.formatNearDate(line.thisYear), line.time.timeText())
        } else {
            line.date.formatNearDate(line.thisYear)
        }
    }
    is LessonLine.Last -> stringResource(R.string.people_last_lesson, line.date.formatNearDate(line.thisYear))
    LessonLine.Never -> stringResource(R.string.people_no_lessons)
}

/** Within a week a weekday is unambiguous; past it «Thu» could be either Thursday. */
private const val DAYS_NAMED = 7
