package com.seblful.klas.ui.homework

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * One piece of homework as a list draws it: who it is for, the lesson it is attached to, and where that
 * leaves it today. Shared by the «Задания» tab and a student's card.
 *
 * @param title the group's name for a group's homework, the student's for one student's.
 * @param lessonAt when the lesson it is attached to starts, or null while none is booked — and always
 * null in the archive, where which lesson it went to is no longer the question.
 * @param due where a current one stands today, or null for a done one.
 */
data class HomeworkRow(
    val homework: Homework,
    val title: String,
    val lessonAt: Instant?,
    val due: HomeworkRules.Due?,
) {
    val isGroup: Boolean get() = homework.assignee is Assignee.Group

    /** Whether it is current — swiped into the archive — rather than done and swiped back out of it. */
    val isCurrent: Boolean get() = !homework.isDone

    val text: String get() = homework.text

    companion object {
        fun current(current: HomeworkRules.Current, captions: Captions, today: LocalDate, zone: TimeZone) =
            HomeworkRow(
                homework = current.homework,
                title = captions.titleOf(current.homework.assignee),
                lessonAt = current.lesson?.start,
                due = current.due(today, zone),
            )

        fun done(homework: Homework, captions: Captions) =
            HomeworkRow(homework = homework, title = captions.titleOf(homework.assignee), lessonAt = null, due = null)

        /** Both halves of [lists], captioned. */
        fun of(lists: HomeworkRules.Lists, captions: Captions, today: LocalDate, zone: TimeZone): HomeworkLists =
            HomeworkLists(
                current = lists.current.map { current(it, captions, today, zone) },
                archive = lists.archive.map { done(it, captions) },
            )
    }
}

/** [HomeworkRules.Lists], captioned: the [current] homework and the [archive] of done ones. */
data class HomeworkLists(val current: List<HomeworkRow>, val archive: List<HomeworkRow>) {
    companion object {
        /**
         * [homework] split into its two lists by [HomeworkRules.lists], attached to [classes] and captioned
         * — the whole of turning stored homework into what the tab and both cards draw, so the three cannot
         * attach or caption it differently.
         *
         * @param today from the view model's `Clock.days()`, never read here: a card left open overnight has
         * to see tomorrow's homework become today's.
         */
        fun of(
            homework: List<Homework>,
            classes: List<TutoringClass>,
            captions: Captions,
            today: LocalDate,
            zone: TimeZone,
        ): HomeworkLists = HomeworkRow.of(HomeworkRules.lists(homework, classes), captions, today, zone)
    }
}

/**
 * Somebody homework can be set for, as the add dialog offers them.
 *
 * @param name the group's name, or the student's.
 * @param nextLessonAt when the lesson homework set for them **now** would be attached to starts, or null
 * while they have none booked — what the dialog says under the text, so the tutor sees where it goes.
 */
data class AssigneeChoice(val assignee: Assignee, val name: String, val nextLessonAt: Instant?) {
    val isGroup: Boolean get() = assignee is Assignee.Group

    companion object {
        /**
         * [assignee] as the dialog offers them at [now]: named [name], with the lesson of [classes] that
         * homework set now would be attached to — see [HomeworkRules.nextLesson]. Taken when the dialog
         * opens, since that lesson moves on as the day does.
         */
        fun of(assignee: Assignee, name: String, classes: List<TutoringClass>, now: Instant): AssigneeChoice =
            AssigneeChoice(assignee, name, HomeworkRules.nextLesson(assignee, now, classes)?.start)
    }
}
