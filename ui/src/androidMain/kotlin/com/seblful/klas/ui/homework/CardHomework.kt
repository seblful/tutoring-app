package com.seblful.klas.ui.homework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.Messages

/** Homework writes bound to one card, with its current add choice read only when requested. */
class CardHomeworkActions(
    private val owner: Assignee,
    private val currentChoice: () -> AssigneeChoice?,
    private val writes: HomeworkActions<*>,
) {
    fun choice(): AssigneeChoice? = currentChoice()

    fun give(text: String) = writes.give(owner, text)

    /** Editing group homework from a student's card keeps the assignment with that group. */
    fun edit(item: Homework, text: String) = writes.edit(item, item.assignee, text)

    fun setDone(item: Homework, done: Boolean) = writes.setDone(item, done)

    fun putBack(item: Homework) = writes.putBack(item)

    fun delete(item: Homework) = writes.delete(item)

    fun restore(item: Homework) = writes.restore(item)
}

/** One card's screen-local dialog and swipe interaction. */
class CardHomework internal constructor(
    private val actions: CardHomeworkActions,
    private val savedDialog: MutableState<HomeworkDialogDraft>,
    private val undo: () -> HomeworkUndo,
) {
    var dialog: HomeworkDialogDraft by savedDialog
        private set

    fun add() {
        dialog = actions.choice()?.let { HomeworkDialogDraft.adding(listOf(it)) } ?: HomeworkDialogDraft()
    }

    fun open(row: HomeworkRow) {
        val own = AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)
        dialog = HomeworkDialogDraft.editing(row.homework, listOf(own))
    }

    fun dismiss() {
        dialog = HomeworkDialogDraft()
    }

    fun swiped(item: Homework, done: Boolean) = undo().swiped(item, done)

    @Composable
    fun Dialogs() {
        val shown = dialog
        if (!shown.open) return
        val original = shown.original?.homework()
        HomeworkDialog(
            choices = shown.choices.map { it.choice() },
            editing = original,
            canChangeAssignee = false,
            onSave = { _, text -> if (original == null) actions.give(text) else actions.edit(original, text) },
            onDelete = original?.let { { undo().deleted(it) } },
            onDismiss = ::dismiss,
        )
    }
}

@Composable
fun rememberCardHomework(actions: CardHomeworkActions, messages: Messages): CardHomework {
    val undo = homeworkUndo(
        messages = messages,
        setDone = actions::setDone,
        putBack = actions::putBack,
        delete = actions::delete,
        restore = actions::restore,
    )
    val currentUndo by rememberUpdatedState(undo)
    val dialog = rememberSerializable { mutableStateOf(HomeworkDialogDraft()) }
    return remember(actions, dialog) { CardHomework(actions, dialog) { currentUndo } }
}
