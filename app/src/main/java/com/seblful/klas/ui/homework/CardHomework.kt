package com.seblful.klas.ui.homework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    private val undo: () -> HomeworkUndo,
) {
    sealed interface Dialog {
        data class Adding(val choice: AssigneeChoice) : Dialog
        data class Editing(val row: HomeworkRow) : Dialog
    }

    var dialog: Dialog? by mutableStateOf(null)
        private set

    fun add() {
        dialog = actions.choice()?.let { Dialog.Adding(it) }
    }

    fun open(row: HomeworkRow) {
        dialog = Dialog.Editing(row)
    }

    fun dismiss() {
        dialog = null
    }

    fun swiped(item: Homework, done: Boolean) = undo().swiped(item, done)

    @Composable
    fun Dialogs() {
        when (val shown = dialog) {
            is Dialog.Adding -> HomeworkDialog(
                choices = listOf(shown.choice),
                onSave = { _, text -> actions.give(text) },
                onDismiss = ::dismiss,
                canChangeAssignee = false,
            )
            is Dialog.Editing -> {
                val row = shown.row
                HomeworkDialog(
                    choices = listOf(AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)),
                    editing = row.homework,
                    canChangeAssignee = false,
                    onSave = { _, text -> actions.edit(row.homework, text) },
                    onDelete = { undo().deleted(row.homework) },
                    onDismiss = ::dismiss,
                )
            }
            null -> Unit
        }
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
    return remember(actions) { CardHomework(actions) { currentUndo } }
}
