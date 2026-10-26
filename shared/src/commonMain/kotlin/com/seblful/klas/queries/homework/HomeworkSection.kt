package com.seblful.klas.queries.homework

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.schedule.CalendarLayout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Where current homework sits on the tab, by the lesson it is for — the tutor scans for past lessons' homework
 * still open, then today's. "Next 7 days" rather than "this week", so a Saturday does not leave Monday's
 * homework under Later.
 */
enum class HomeworkSection {
    OVERDUE,
    TODAY,
    NEXT_7_DAYS,
    LATER,
    UNBOOKED,
    ;

    companion object {
        fun of(due: HomeworkRules.Due, lessonAt: Instant?, today: LocalDate, zone: TimeZone): HomeworkSection =
            when (due) {
                HomeworkRules.Due.OVERDUE -> OVERDUE
                HomeworkRules.Due.TODAY -> TODAY
                HomeworkRules.Due.UNBOOKED -> UNBOOKED
                HomeworkRules.Due.LATER -> {
                    val day = lessonAt?.toLocalDateTime(zone)?.date
                    val weekOn = today.plus(DatePeriod(days = CalendarLayout.DAYS_IN_WEEK))
                    if (day != null && day < weekOn) NEXT_7_DAYS else LATER
                }
            }
    }
}

/**
 * [rows] — current homework, in [HomeworkRules.lists]'s order — cut into its sections, each kept in that
 * order and listed only when it holds something. The order already runs overdue, today, later, unbooked,
 * so the sections come in [HomeworkSection]'s order too.
 */
fun bySection(rows: List<HomeworkRow>): List<Pair<HomeworkSection, List<HomeworkRow>>> =
    rows.mapNotNull { row -> row.section?.let { it to row } }
        .groupBy({ it.first }, { it.second })
        .toList()
        .sortedBy { (section, _) -> section.ordinal }
