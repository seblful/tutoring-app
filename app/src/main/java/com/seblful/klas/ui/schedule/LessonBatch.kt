package com.seblful.klas.ui.schedule

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.SessionReceipt
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.settleAfterUndoWindow
import com.seblful.klas.ui.common.state.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lessons picked by a long-press, and what can be done to them at once: marked taught, or cancelled — the two
 * batch actions safe enough to offer, each with **one** undo for the lot. Picked by a class of each lesson, so
 * a group lesson is one pick. Only planned lessons are touched; the rest of a selection is left as it is.
 *
 * @param scope the app's, as every lesson write's: the selection bar closes as the action is tapped.
 */
class LessonBatch(private val classes: ClassRepository, private val scope: CoroutineScope) {

    sealed interface Done {
        data class Marked(val sessions: List<ClassSession>) : Done

        data class Cancelled(val receipts: List<SessionReceipt>) : Done

        /** An undo left [count] lessons that had changed since, as every guarded undo does. */
        data class ChangedSince(val count: Int) : Done

        data object Failed : Done
    }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** A class id of each picked lesson. */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    val done = UiEvents<Done>()

    fun toggle(classId: Long) = _selected.update { if (classId in it) it - classId else it + classId }

    fun clear() {
        _selected.value = emptySet()
    }

    fun markTaught() = act { sessions ->
        val marked = sessions.mapNotNull { session ->
            (classes.setStatus(session, LessonStatus.COMPLETED) as? LessonWrite.Written)?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let { ClassSession(session.groupId, it) }
        }
        if (marked.isNotEmpty()) done.send(Done.Marked(marked))
    }

    fun cancel() = cancel(_selected.value)

    /** Cancels the planned lessons among [classIds] — a whole day's, from its header — with one undo. */
    fun cancel(classIds: Set<Long>) = act(classIds) { sessions ->
        val receipts = sessions.mapNotNull { session ->
            (classes.setStatusUndoable(session, LessonStatus.CANCELLED) as? LessonWrite.Written)?.value
        }
        if (receipts.isEmpty()) return@act
        done.send(Done.Cancelled(receipts))
        // As a single cancel: the calendar hears of it only once the undo has had its chance.
        scope.settleAfterUndoWindow(done, Done.Failed) { classes.settleAll(receipts) }
    }

    /** Takes [batch] back, each lesson only if nothing about it has changed since. */
    fun undo(batch: Done) = scope.write(done, Done.Failed) {
        val kept = when (batch) {
            is Done.Marked -> batch.sessions.count { !classes.correctCompletion(it, unchanged = it) }
            is Done.Cancelled -> classes.undoAll(batch.receipts).count { it != UndoResult.Restored }
            else -> 0
        }
        if (kept > 0) done.send(Done.ChangedSince(kept))
    }

    private fun act(ids: Set<Long> = _selected.value, block: suspend (List<ClassSession>) -> Unit) {
        clear()
        scope.write(done, Done.Failed) {
            val planned = ids.mapNotNull { classes.getSessionFor(it) }.filter { it.status == LessonStatus.SCHEDULED }
            block(planned)
        }
    }
}
