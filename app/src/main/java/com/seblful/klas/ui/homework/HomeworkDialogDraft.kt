package com.seblful.klas.ui.homework

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.AssigneeChoice
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Saved-state form of an open homework editor; [original] is kept for guarded corrections. */
@Serializable
data class HomeworkDialogDraft(
    val choices: List<HomeworkChoiceDraft> = emptyList(),
    val original: HomeworkOriginal? = null,
) {
    val open: Boolean get() = choices.isNotEmpty()

    companion object {
        fun adding(choices: List<AssigneeChoice>): HomeworkDialogDraft =
            HomeworkDialogDraft(choices.map(HomeworkChoiceDraft::of))

        fun editing(homework: Homework, choices: List<AssigneeChoice>): HomeworkDialogDraft =
            HomeworkDialogDraft(choices.map(HomeworkChoiceDraft::of), HomeworkOriginal.of(homework))
    }
}

/** An [Assignee] as saved state, which cannot hold a sealed type. */
@Serializable
data class SavedAssignee(val group: Boolean, val id: Long) {
    fun assignee(): Assignee = if (group) Assignee.Group(id) else Assignee.Student(id)

    companion object {
        fun of(assignee: Assignee): SavedAssignee = when (assignee) {
            is Assignee.Student -> SavedAssignee(false, assignee.studentId)
            is Assignee.Group -> SavedAssignee(true, assignee.groupId)
        }
    }
}

@Serializable
data class HomeworkChoiceDraft(val who: SavedAssignee, val name: String, val lessonAt: Instant?) {
    fun choice(): AssigneeChoice = AssigneeChoice(who.assignee(), name, lessonAt)

    companion object {
        fun of(choice: AssigneeChoice) =
            HomeworkChoiceDraft(SavedAssignee.of(choice.assignee), choice.name, choice.nextLessonAt)
    }
}

@Serializable
data class HomeworkOriginal(
    val id: Long,
    val who: SavedAssignee,
    val text: String,
    val givenAt: Instant,
    val doneAt: Instant?,
) {
    fun homework(): Homework = Homework(id, who.assignee(), text, givenAt, doneAt)

    companion object {
        fun of(homework: Homework) = HomeworkOriginal(
            homework.id, SavedAssignee.of(homework.assignee), homework.text, homework.givenAt, homework.doneAt,
        )
    }
}

internal fun Assignee.editorKey(): String = when (this) {
    is Assignee.Student -> "student:$studentId"
    is Assignee.Group -> "group:$groupId"
}
