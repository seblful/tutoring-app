package com.seblful.klas.preview

import androidx.compose.runtime.Composable
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.OverviewSection
import com.seblful.klas.ui.common.sectionRowColors
import com.seblful.klas.ui.groups.EarnedCard
import com.seblful.klas.ui.groups.GroupCard
import com.seblful.klas.ui.groups.GroupLessonItem
import com.seblful.klas.ui.groups.GroupLessonRow
import com.seblful.klas.ui.groups.GroupRow
import com.seblful.klas.ui.people.LessonLine
import com.seblful.klas.ui.people.NextLesson
import com.seblful.klas.ui.people.NextLessonCard
import com.seblful.klas.ui.students.BalanceCard
import com.seblful.klas.ui.students.PaymentRow
import com.seblful.klas.ui.students.StudentCard
import com.seblful.klas.ui.students.StudentRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

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

/** A student card's next lesson — a group one, so its line names the group — and none booked. */
@KlasPreview
@Composable
private fun StudentNextLessonPreview() = PreviewSurface {
    NextLessonCard(
        NextLesson(
            classId = 1,
            line = LessonLine.Next(LocalDate(2026, 6, 4), LocalTime(17, 0), daysAway = 3, thisYear = 2026),
            running = false,
            subjectName = "Физика",
            durationMinutes = 90,
            groupName = "Олимпиадная физика",
        ),
        onOpen = {},
        onBook = {},
    )
    NextLessonCard(next = null, onOpen = {}, onBook = {})
}

/** What a student owes, and a section showing two of its three rows with the way to the rest. */
@KlasPreview
@Composable
private fun StudentBalancePreview() = PreviewSurface {
    val payments = listOf(
        Payment(id = 1, studentId = 1, date = Instant.parse("2026-05-18T12:00:00Z"), amount = Money.ofMajor(120)),
        Payment(id = 2, studentId = 1, date = Instant.parse("2026-04-30T12:00:00Z"), amount = Money.ofMajor(90)),
    )
    BalanceCard(
        StudentDetails(
            balance = Balance.Owed(Money.ofMajor(200)),
            totals = Taught.Totals(
                lessons = 27, upcoming = 6, cancelled = 1, minutes = 1260, earned = Money.ofMajor(862),
            ),
            payments = payments,
        ),
        onAddPayment = {},
    )
    OverviewSection(title = "Платежи", count = 3, onSeeAll = {}) {
        payments.forEach { PaymentRow(it, onEdit = {}, colors = sectionRowColors()) }
    }
}

/** What a group has earned, and its lessons section: one everybody came to, one somebody missed, one ahead. */
@KlasPreview
@Composable
private fun GroupOverviewPreview() = PreviewSurface {
    EarnedCard(
        Taught.Totals(lessons = 18, upcoming = 4, cancelled = 0, minutes = 1620, earned = Money.ofMajor(1440)),
    )
    fun lesson(id: Long, start: String, status: LessonStatus, missed: Boolean = false) = GroupLessonRow(
        session = ClassSession(
            groupId = 1,
            classes = (1L..3L).map { member ->
                TutoringClass(
                    id = id * 10 + member, lessonId = id, studentId = member, subjectId = 1,
                    start = Instant.parse(start), durationMinutes = 90, price = Money.ofMajor(30), status = status,
                    attended = if (missed && member == 3L) Attended.ABSENT else Attended.PRESENT,
                )
            },
        ),
        charge = Money.ofMajor(if (missed) 60 else 90).takeIf { status == LessonStatus.COMPLETED },
    )
    OverviewSection(title = "Занятия", count = 22, onSeeAll = {}) {
        listOf(
            lesson(3, "2026-05-28T15:00:00Z", LessonStatus.COMPLETED, missed = true),
            lesson(2, "2026-05-21T15:00:00Z", LessonStatus.COMPLETED),
            lesson(1, "2026-05-14T15:00:00Z", LessonStatus.CANCELLED),
        ).forEach { GroupLessonItem(it, onClick = {}, colors = sectionRowColors()) }
    }
}
