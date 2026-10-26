package com.seblful.klas.queries.groups

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.CardLessons
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.queries.homework.HomeworkLists
import kotlin.time.Instant

/**
 * A member as the group's card lists them: of the group's taught lessons they were booked into ([held]),
 * how many they were at ([present]) — what a tutor checks about a group — and where their money stands.
 */
data class GroupMemberRow(
    val student: Student,
    val held: Int,
    val present: Int,
    val balance: Balance = Balance.Settled,
)

/**
 * One of the group's lessons: the classes of a single session, plus what it came to.
 *
 * @param charge the whole lesson's recognized income — the sum over its members, since each of them
 * is billed the group's rate separately (see `Group.price`) — or null where it billed nobody. See
 * `Billing.billedOrNull`.
 */
data class GroupLessonRow(
    val session: ClassSession,
    val charge: Money?,
) {
    val attendance: Attendance get() = session.attendance
}

data class GroupDetails(
    val group: Group? = null,
    /** What the group is taught, or null for a group made before subjects were fixed on the group. */
    val subjectName: String? = null,
    /** Lessons and hours taught, and what they earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val members: List<GroupMemberRow> = emptyList(),
    val lessons: List<GroupLessonRow> = emptyList(),
    /** The group's own homework, current and done — the card's homework view. See [HomeworkRules.lists]. */
    val homework: HomeworkLists = HomeworkLists(current = emptyList(), archive = emptyList()),
) {
    /**
     * Whether «Удалить» is offered at all. A group is archive-only once it has lessons — they are billed
     * history — so the menu must not ask «Удалить группу?» only to refuse the answer it had asked for.
     */
    val canDelete: Boolean get() = lessons.isEmpty()

    /** How many planned lessons archiving would call off — asked about first when there are any. */
    val plannedLessons: Int get() = lessons.count { it.session.status == LessonStatus.SCHEDULED }

    /** The lesson the tutor next teaches the group — see [CardLessons.next]. */
    fun nextLesson(now: Instant): GroupLessonRow? = CardLessons.next(lessons, now) { it.session.first }

    /** The few lessons the card shows under its lessons heading — see [CardLessons.preview]. */
    fun lessonPreview(now: Instant, limit: Int): List<GroupLessonRow> =
        CardLessons.preview(lessons, now, limit) { it.session.first }
}
