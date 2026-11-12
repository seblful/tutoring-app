package com.seblful.klas.ui.schedule

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.lessons.LessonChange
import com.seblful.klas.lessons.LessonChanges
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lessons picked by a long-press, and what can be done to them at once: marked taught, cancelled, or — when
 * every one picked is cancelled — returned to planned; each with **one** undo for the lot. Picked by a class of
 * each lesson, so a group lesson is one pick. Each action touches the lessons the lesson's own sheet would let
 * it — a planned one is marked taught; a planned or a taught one is cancelled; a cancelled one re-planned — and
 * leaves the rest as they are, saying so when that is all of them ([Done.Untouched]): an action that does
 * nothing must not look like one that failed.
 *
 * @param scope the app's, as every lesson write's: the selection bar closes as the action is tapped.
 */
class LessonBatch(
    private val classes: ClassRepository,
    private val lessonChanges: LessonChanges,
    private val scope: CoroutineScope,
) {

    sealed interface Done {
        data class Marked(val changes: List<LessonChange>) : Done

        data class Cancelled(val changes: List<LessonChange>) : Done

        /** Back in the plan; [clashed] stayed cancelled, their hour taken by another lesson since. */
        data class Replanned(val changes: List<LessonChange>, val clashed: Int) : Done

        /** None of the picked lessons could take [action]: marking taught what is not planned, cancelling the cancelled. */
        data class Untouched(val action: Action) : Done

        /** An undo left [count] lessons that had changed since, as every guarded undo does. */
        data class ChangedSince(val count: Int) : Done

        data object Failed : Done
    }

    enum class Action { MARK_TAUGHT, CANCEL, REPLAN }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** A class id of each picked lesson. */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    val done = UiEvents<Done>()

    private val changes = lessonChanges.inScope(scope) { done.send(Done.Failed) }

    fun toggle(classId: Long) = _selected.update { if (classId in it) it - classId else it + classId }

    fun clear() {
        _selected.value = emptySet()
    }

    fun markTaught() {
        val ids = _selected.value
        clear()
        scope.write(done, Done.Failed) {
            val marked = lessonChanges.complete(ids)
            if (marked.isEmpty()) done.send(Done.Untouched(Action.MARK_TAUGHT))
            else done.send(Done.Marked(marked))
        }
    }

    /** Cancels the picked lessons, taught ones too, as each one's sheet would. */
    fun cancel() = cancel(_selected.value, sheetAllows(LessonStatus.CANCELLED))

    /**
     * Cancels the planned lessons among [classIds] — a whole day's, from its header — with one undo. A day's
     * taught lessons stay taught: they are cancelled one at a time, knowingly, as what they earned goes too.
     */
    fun cancelDay(classIds: Set<Long>) = cancel(classIds) { it.status == LessonStatus.SCHEDULED }

    private fun cancel(classIds: Set<Long>, eligible: (ClassSession) -> Boolean) =
        act(classIds, Action.CANCEL, eligible) { sessions ->
            val undoable = changes.setStatus(sessions, LessonStatus.CANCELLED)
                .mapNotNull { (it as? LessonWrite.Written)?.value }
            if (undoable.isEmpty()) return@act
            done.send(Done.Cancelled(undoable))
        }

    /** Returns the picked cancelled lessons to the plan, each whose hour is still free. */
    fun replan() = act(_selected.value, Action.REPLAN, sheetAllows(LessonStatus.SCHEDULED)) { sessions ->
        val writes = changes.setStatus(sessions, LessonStatus.SCHEDULED)
        val undoable = writes.mapNotNull { (it as? LessonWrite.Written)?.value }
        val clashed = writes.count { it is LessonWrite.HourTaken }
        if (undoable.isEmpty() && clashed == 0) return@act
        done.send(Done.Replanned(undoable, clashed))
    }

    /** Takes [batch] back, each lesson only if nothing about it has changed since. */
    fun undo(batch: Done) = scope.write(done, Done.Failed) {
        val kept = when (batch) {
            is Done.Marked -> batch.changes
            is Done.Cancelled -> batch.changes
            is Done.Replanned -> batch.changes
            else -> emptyList()
        }.let { changes.undoAll(it).count { result -> result != UndoResult.Restored } }
        if (kept > 0) done.send(Done.ChangedSince(kept))
    }

    private fun act(
        ids: Set<Long>,
        action: Action,
        eligible: (ClassSession) -> Boolean,
        block: suspend (List<ClassSession>) -> Unit,
    ) {
        clear()
        scope.write(done, Done.Failed) {
            val touched = ids.mapNotNull { classes.getSessionFor(it) }.filter(eligible)
            if (touched.isEmpty()) done.send(Done.Untouched(action)) else block(touched)
        }
    }

    /** What a lesson's own sheet would let it become. */
    private fun sheetAllows(status: LessonStatus): (ClassSession) -> Boolean =
        { status in SessionRules.sessionTransitions(it) }
}
