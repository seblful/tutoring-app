package com.seblful.klas.preview

import androidx.compose.runtime.Composable
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.groups.GroupCard
import com.seblful.klas.ui.groups.GroupRow
import com.seblful.klas.ui.people.LessonLine
import com.seblful.klas.ui.students.StudentCard
import com.seblful.klas.ui.students.StudentRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * The two sides of «Люди», card by card — the same anatomy on both, which is what these are here to keep.
 * Same rules as `CommonPreviews.kt`: Russian text, plausible numbers, every preview a screenshot test.
 */

/**
 * Every line a student's card can say: a debt, a credit, nothing owed; a lesson today, this week, past a
 * week; the last one when none is booked; none at all; and the archived badge beside a debt.
 */
@KlasPreview
@Composable
private fun StudentCardsPreview() = PreviewSurface {
    listOf(
        StudentRow(
            Student(id = 1, name = "Аня Смирнова"), Money.ofMajor(30), listOf("Математика"),
            LessonLine.Next(LocalDate(2026, 6, 1), LocalTime(16, 0), daysAway = 0, thisYear = 2026),
        ),
        StudentRow(
            Student(id = 2, name = "Артём Новиков"), Money.ZERO, listOf("Математика", "Физика"),
            LessonLine.Next(LocalDate(2026, 6, 4), LocalTime(17, 30), daysAway = 3, thisYear = 2026),
        ),
        StudentRow(
            Student(id = 5, name = "Ольга Кузнецова"), Money.ofMajor(-80), listOf("Английский язык"),
            LessonLine.Next(LocalDate(2026, 6, 24), LocalTime(10, 0), daysAway = 23, thisYear = 2026),
        ),
        StudentRow(
            Student(id = 6, name = "Константин Александрович Преображенский"), Money.ZERO, listOf("Химия"),
            LessonLine.Last(LocalDate(2026, 4, 12), thisYear = 2026),
        ),
        StudentRow(Student(id = 8, name = "Мила Соколова"), Money.ZERO),
        StudentRow(
            Student(id = 9, name = "Даниил Волков", archived = true), Money.ofMajor(45), listOf("Физика"),
            LessonLine.Last(LocalDate(2025, 12, 20), thisYear = 2026),
        ),
    ).forEach { StudentCard(it, onClick = {}) }
}

/** A group with a lesson tomorrow, one with nobody in it yet, and one in the archive. */
@KlasPreview
@Composable
private fun GroupCardsPreview() = PreviewSurface {
    listOf(
        GroupRow(
            Group(id = 1, name = "Подготовка к ЦТ"), listOf("Даниил", "Мила", "Пётр", "Таня"), "Математика",
            LessonLine.Next(LocalDate(2026, 6, 2), LocalTime(18, 0), daysAway = 1, thisYear = 2026),
        ),
        GroupRow(Group(id = 2, name = "Олимпиадная физика"), emptyList(), "Физика"),
        GroupRow(
            Group(id = 3, name = "11 «А»", archived = true), listOf("Аня", "Борис"), "Химия",
            LessonLine.Last(LocalDate(2026, 5, 28), thisYear = 2026),
        ),
    ).forEach { GroupCard(it, onClick = {}) }
}
