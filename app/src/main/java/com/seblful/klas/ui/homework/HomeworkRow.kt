package com.seblful.klas.ui.homework

import com.seblful.klas.domain.Captions
import com.seblful.klas.domain.Homework
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * One lesson's homework as a list draws it: who it was set for, in what, and each of them beside their
 * own verdict.
 *
 * Shared by the tab and the schedule's sheet, which ask the same thing of it — the tab for every lesson
 * with homework open, the sheet for the one its lesson checks.
 *
 * @param title the group's name for a group's homework, the student's for an individual lesson's.
 * @param members everybody it was set for, each named beside their class — one for an individual lesson.
 *   Their class carries the verdict ([TutoringClass.homeworkResult]) and is what a verdict is written to.
 */
data class HomeworkRow(
    val assignment: Homework.Assignment,
    val title: String,
    val subjectName: String,
    val members: List<Member>,
) {
    /** One student it was set for, by name, beside their own class. */
    data class Member(val klass: TutoringClass, val name: String)

    val text: String get() = assignment.text

    /** Whether it was a group's — which is when each member is named, since the title names the group. */
    val isGroup: Boolean get() = assignment.set.isGroup

    /** When the lesson it was set at was. */
    val setAt: Instant get() = assignment.set.start

    /** When the lesson it is due at starts, or null while none is booked. */
    val dueAt: Instant? get() = assignment.due?.start

    /** The class a tap opens — the lesson it was set at, where the homework itself is edited. */
    val lessonClassId: Long get() = assignment.set.first.id

    companion object {
        fun of(assignment: Homework.Assignment, captions: Captions): HomeworkRow = HomeworkRow(
            assignment = assignment,
            title = captions.titleOf(assignment.set),
            subjectName = captions.subjectOf(assignment.set),
            members = assignment.set.classes.map { Member(it, captions.studentOf(it)) },
        )
    }
}
