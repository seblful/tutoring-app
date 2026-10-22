package com.seblful.klas.ui.groups

import com.seblful.klas.R
import com.seblful.klas.ui.people.NextLesson
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The group's card as of a minute: [details], with the lesson the tutor next teaches it and the few listed
 * under the lessons heading read off the same snapshot — see [GroupDetailState.nextLesson] and
 * [GroupDetailState.lessonPreview].
 */
data class GroupOverview(val details: GroupDetailState, val next: NextLesson?, val lessons: List<GroupLessonRow>) {

    companion object {
        /** How many lessons the card lists before «See all». */
        const val LESSONS_SHOWN = 3

        fun of(details: GroupDetailState, now: Instant, zone: TimeZone): GroupOverview = GroupOverview(
            details = details,
            next = details.nextLesson(now)?.let { row ->
                NextLesson.of(row.session.first, details.subjectName, groupName = null, now, zone)
            },
            lessons = details.lessonPreview(now, LESSONS_SHOWN),
        )
    }
}

/** A group's list that can run long, shown in full on its own page. Members are few, and stay on the card. */
enum class GroupSection(val titleRes: Int) {
    LESSONS(R.string.group_lessons_title),

    /** Current and done — the card shows only the first few current ones. */
    HOMEWORK(R.string.student_homework_title),
}
