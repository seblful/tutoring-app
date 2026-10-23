package com.seblful.klas.queries.homework

import com.seblful.klas.domain.homework.HomeworkRules.Due
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HomeworkSectionTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 6, 6)

    private fun later(at: String) = HomeworkSection.of(Due.LATER, Instant.parse(at), today, zone)

    @Test
    fun `a later lesson within seven days is in the next seven days, and one a week out is later`() {
        assertEquals(HomeworkSection.NEXT_7_DAYS, later("2026-06-07T09:00:00Z"))
        assertEquals(HomeworkSection.NEXT_7_DAYS, later("2026-06-12T23:00:00Z"))
        assertEquals(HomeworkSection.LATER, later("2026-06-13T09:00:00Z"))
    }

    @Test
    fun `overdue, today and unbooked keep their own sections`() {
        assertEquals(HomeworkSection.OVERDUE, HomeworkSection.of(Due.OVERDUE, null, today, zone))
        assertEquals(HomeworkSection.TODAY, HomeworkSection.of(Due.TODAY, null, today, zone))
        assertEquals(HomeworkSection.UNBOOKED, HomeworkSection.of(Due.UNBOOKED, null, today, zone))
    }

    @Test
    fun `rows are cut into their sections in order, empty sections left out, done rows dropped`() {
        fun row(id: Long, section: HomeworkSection?) = HomeworkRow(
            homework = Homework(id, Assignee.Student(1), "Task $id", Instant.parse("2026-06-01T09:00:00Z")),
            title = "Аня",
            lessonAt = null,
            due = null,
            section = section,
        )
        val rows = listOf(
            row(1, HomeworkSection.OVERDUE),
            row(2, HomeworkSection.TODAY),
            row(3, HomeworkSection.TODAY),
            row(4, HomeworkSection.UNBOOKED),
            row(5, null),
        )

        val sections = bySection(rows).map { (section, inIt) -> section to inIt.map { it.homework.id } }

        assertEquals(
            listOf(
                HomeworkSection.OVERDUE to listOf(1L),
                HomeworkSection.TODAY to listOf(2L, 3L),
                HomeworkSection.UNBOOKED to listOf(4L),
            ),
            sections,
        )
    }
}
