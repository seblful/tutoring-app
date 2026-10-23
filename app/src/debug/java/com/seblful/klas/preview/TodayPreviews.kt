package com.seblful.klas.preview

import androidx.compose.runtime.Composable
import com.seblful.klas.domain.guide.FirstStep
import com.seblful.klas.domain.guide.FirstSteps
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.today.FirstStepsCard
import com.seblful.klas.ui.today.TodayActions
import com.seblful.klas.ui.today.TodayContent
import com.seblful.klas.ui.today.TodayLessonRow
import com.seblful.klas.ui.today.TodayState
import kotlin.time.Instant

/*
 * «Сегодня» as a tutor meets it mid-afternoon: a backup never made, a lesson on now, one over and unmarked,
 * one this evening, two students owing and homework due. Same rules as `CommonPreviews.kt`.
 */

private fun row(id: Long, who: String, start: String, phase: LessonPhase, status: LessonStatus) = TodayLessonRow(
    session = ClassSession(
        groupId = null,
        classes = listOf(
            TutoringClass(
                id = id, lessonId = id, studentId = id, subjectId = 1, start = Instant.parse(start),
                durationMinutes = LESSON_MINUTES, price = LessonPrice, status = status,
            ),
        ),
    ),
    who = who,
    subject = "Математика",
    phase = phase,
)

private val busyDay = TodayState(
    lessons = listOf(
        row(1, "Аня Смирнова", "2026-06-01T07:00:00Z", LessonPhase.SETTLED, LessonStatus.COMPLETED),
        row(2, "Артём Новиков", "2026-06-01T09:00:00Z", LessonPhase.OVERDUE, LessonStatus.SCHEDULED),
        row(3, "Ольга Кузнецова", "2026-06-01T12:00:00Z", LessonPhase.IN_PROGRESS, LessonStatus.SCHEDULED),
        row(4, "Пётр Иванов", "2026-06-01T15:00:00Z", LessonPhase.UPCOMING, LessonStatus.SCHEDULED),
    ),
    outstanding = Reports.Outstanding(students = 2, total = Money.ofMajor(110)),
    homework = listOf(
        HomeworkRow(
            Homework(
                id = 1, assignee = Assignee.Student(4), text = "§ 12, упр. 4–7",
                givenAt = Instant.parse("2026-05-29T15:00:00Z"),
            ),
            title = "Пётр Иванов",
            lessonAt = Instant.parse("2026-06-01T15:00:00Z"),
            due = HomeworkRules.Due.TODAY,
        ),
    ),
    lastBackupAt = null,
    backupStale = true,
)

private const val LESSON_MINUTES = 60
private val LessonPrice = Money.ofMajor(30)

@KlasPreview
@Composable
private fun TodayPreview() = PreviewSurface { TodayContent(busyDay, TodayActions()) }

/** A day off: no lessons, nothing owed — the day's card still says so rather than vanishing. */
@KlasPreview
@Composable
private fun TodayEmptyPreview() = PreviewSurface {
    TodayContent(
        TodayState(
            lessons = emptyList(),
            outstanding = Reports.Outstanding(students = 0, total = Money.ZERO),
            homework = emptyList(),
            lastBackupAt = Instant.parse("2026-05-30T10:00:00Z"),
            backupStale = false,
        ),
        TodayActions(),
    )
}

/** A new tutor's first day: one lesson booked for the evening, and the first steps still mostly to do. */
@KlasPreview
@Composable
private fun TodayFirstStepsPreview() = PreviewSurface {
    TodayContent(
        TodayState(
            lessons = listOf(
                row(4, "Пётр Иванов", "2026-06-01T15:00:00Z", LessonPhase.UPCOMING, LessonStatus.SCHEDULED),
            ),
            outstanding = Reports.Outstanding(students = 0, total = Money.ZERO),
            homework = emptyList(),
            lastBackupAt = null,
            backupStale = false,
            firstSteps = FirstStepsCard(FirstSteps(setOf(FirstStep.BOOK, FirstStep.REMINDERS)), student = 4),
        ),
        TodayActions(),
    )
}
