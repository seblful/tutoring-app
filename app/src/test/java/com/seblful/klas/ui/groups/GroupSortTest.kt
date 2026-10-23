package com.seblful.klas.ui.groups

import com.seblful.klas.domain.model.Group
import com.seblful.klas.ui.people.LessonLine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSortTest {

    private fun row(name: String, lessons: LessonLine = LessonLine.Never, archived: Boolean = false) =
        GroupRow(Group(name = name, archived = archived), memberNames = emptyList(), lessons = lessons)

    private fun next(day: Int, hour: Int) =
        LessonLine.Next(LocalDate(2026, 6, day), LocalTime(hour, 0), daysAway = day - 1, thisYear = 2026)

    private val rows = listOf(
        row("Алгебра", next(day = 3, hour = 18)),
        row("Биология"),
        row("Вечерняя", next(day = 1, hour = 19)),
        row("Геометрия", next(day = 1, hour = 9), archived = true),
        row("Дневная", next(day = 1, hour = 16)),
    )

    @Test
    fun `by name keeps the name order, the archive last`() {
        assertEquals(
            listOf("Алгебра", "Биология", "Вечерняя", "Дневная", "Геометрия"),
            rows.sortedFor(GroupSort.NAME).map { it.group.name },
        )
    }

    @Test
    fun `by next lesson puts the soonest first, a group with none booked after, the archive last`() {
        assertEquals(
            listOf("Дневная", "Вечерняя", "Алгебра", "Биология", "Геометрия"),
            rows.sortedFor(GroupSort.NEXT_LESSON).map { it.group.name },
        )
    }
}
