package com.seblful.klas.queries.homework

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import kotlin.time.Instant

/** Active owners' homework, narrowed for a list, with choices for its filter and add dialog. */
data class HomeworkState(
    val lists: HomeworkLists,
    val filter: Assignee?,
    val assignees: List<Assignee>,
    val filterAssignees: List<Assignee>,
    val captions: Captions,
    private val classes: List<TutoringClass>,
) {
    val filterName: String? get() = filter?.let(captions::titleOf)

    /** Read when Add opens: nearest lessons first, with attachment previews from this instant. */
    fun choices(now: Instant): List<AssigneeChoice> =
        HomeworkRules.byNearestLesson(assignees, classes, now).map { who ->
            AssigneeChoice.of(who, captions.titleOf(who), classes, now)
        }
}
