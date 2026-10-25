package com.seblful.klas.lessons

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.domain.lesson.CompletionTiming
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Completion mirrors immediately; its guarded correction never restores an older lesson snapshot. */
@SingleIn(AppScope::class)
class LessonCompletions @Inject constructor(private val classes: ClassRepository, private val clock: WallClock) {
    /** Returns the whole written lesson, or null when it is gone or no longer eligible. */
    suspend fun complete(classId: Long): ClassSession? = complete(listOf(classId)).singleOrNull()

    /** One transaction, one result per changed lesson; returned snapshots are the receipts for [undo]. */
    suspend fun complete(
        classIds: Collection<Long>,
        timing: CompletionTiming = CompletionTiming.ANY_TIME,
    ): List<ClassSession> = classes.complete(classIds) { timing.allows(LessonPhase.of(it, clock.now())) }

    /** Only takes back a mark while the lesson is still as completion left it. */
    suspend fun undo(completed: ClassSession): Boolean = classes.correctCompletion(completed, unchanged = completed)

    /** Returns how many lessons stayed taught because they changed or disappeared. */
    suspend fun undoAll(completed: List<ClassSession>): Int = completed.count { !undo(it) }

    /** Explicit correction works on the current lesson, preserving later edits. */
    suspend fun correct(session: ClassSession): Boolean = classes.correctCompletion(session)
}
