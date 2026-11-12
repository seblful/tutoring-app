package com.seblful.klas.data.lessons

import com.seblful.klas.domain.model.ClassSession

/**
 * What one undoable lesson write changed: the lesson [before] it, and as the write left it ([after]).
 *
 * An undo writes [before] back only while the lesson is still [after] — calendar ids aside, which are
 * mirrors — so a receipt can never undo an edit made since. It is the guard the completion correction
 * uses (`ClassRepository.undoCompletion`), for every change that can be taken back.
 */
data class SessionReceipt(val before: ClassSession, val after: ClassSession) {
    val lessonId: Long get() = after.lessonId
}

/** What became of an undo. Only [Restored] wrote anything. */
sealed interface UndoResult {

    data object Restored : UndoResult

    /** The lesson was edited, re-marked or moved since; undoing would overwrite that. */
    data object ChangedSince : UndoResult

    /** Restoring would put the lesson back in an hour [clash] has taken since. */
    data class HourTaken(val clash: ClassSession) : UndoResult

    /** The lesson was deleted since. */
    data object Gone : UndoResult
}
