package com.seblful.klas.lessons

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.SessionReceipt
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.data.lessons.WritesWithoutUndo
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.domain.lesson.CompletionTiming
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A guarded undo, without exposing the rows or calendar holds it restores.
 *
 * @property lesson the lesson as this change left it.
 */
class LessonChange private constructor(val lesson: ClassSession, internal val receipt: SessionReceipt?) {
    internal companion object {
        /** A write whose calendar waits out the undo window; its undo writes the receipt's `before` back. */
        fun held(receipt: SessionReceipt) = LessonChange(receipt.after, receipt)

        /** A completion, mirrored at once; its undo corrects the mark while the lesson is still [completed]. */
        fun completed(completed: ClassSession) = LessonChange(completed, receipt = null)
    }
}

/** An archived owner and the planned lessons called off by that archive. */
class ArchiveChange internal constructor(
    internal val owner: Assignee,
    internal val receipts: List<SessionReceipt>,
) {
    val cancelledLessons: Int get() = receipts.size
}

/** Longer than a long snackbar, so the calendar waits until its Undo has had a chance. */
val UndoWindow: Duration = 12.seconds

/**
 * Undoable lesson and archive writes, including their deferred calendar settlement. Marking a lesson taught
 * goes through [complete] (a [Writer]'s `setStatus` to completed routes there): it mirrors at once, needs no
 * scope, and its [LessonChange] is undone by the same [Writer.undo] as every other change.
 */
@SingleIn(AppScope::class)
class LessonChanges @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val groups: GroupRepository,
    private val clock: WallClock,
) {
    /**
     * Archives [student] at once, with no undo window: their planned lessons are called off in the archive's
     * transaction and the calendar follows straight away. Returns how many lessons it called off.
     */
    @WritesWithoutUndo
    suspend fun archiveWithoutUndo(student: Student): Int =
        students.archive(student).also { classes.settleAll(it) }.size

    /** [archiveWithoutUndo] for [group]. */
    @WritesWithoutUndo
    suspend fun archiveWithoutUndo(group: Group): Int =
        groups.archive(group).also { classes.settleAll(it) }.size

    /** Marks the whole lesson [classId] belongs to taught; null when it is gone or no longer eligible. */
    suspend fun complete(classId: Long): LessonChange? = complete(listOf(classId)).singleOrNull()

    /**
     * Marks taught each lesson among [classIds] that may be, judged against the stored rows and [timing] inside
     * one transaction; one change per lesson written.
     */
    suspend fun complete(
        classIds: Collection<Long>,
        timing: CompletionTiming = CompletionTiming.ANY_TIME,
    ): List<LessonChange> =
        classes.complete(classIds) { timing.allows(LessonPhase.of(it, clock.now())) }.map(LessonChange::completed)

    /** Corrects [session]'s current completion, preserving later edits; false when it is not taught. */
    suspend fun correctCompletion(session: ClassSession): Boolean = classes.correctCompletion(session)

    /** [scope] belongs to the host application; leaving a screen must not cancel settlement. */
    fun inScope(scope: CoroutineScope, onFailure: (Throwable) -> Unit): Writer = Writer(scope, onFailure)

    inner class Writer internal constructor(
        private val scope: CoroutineScope,
        private val onFailure: (Throwable) -> Unit,
    ) {
        /** Completed routes to [complete]: a null change when the lesson may not be marked taught. */
        suspend fun setStatus(session: ClassSession, status: LessonStatus): LessonWrite<LessonChange?> {
            if (status != LessonStatus.COMPLETED) return classes.setStatusUndoable(session, status).track()
            val change = complete(session.first.id)
            return when {
                change != null -> LessonWrite.Written(change)
                classes.getSessionFor(session.first.id) == null -> LessonWrite.Gone
                else -> LessonWrite.Written(null)
            }
        }

        /** The undo window starts after the batch, including when a later write fails. */
        suspend fun setStatus(
            sessions: List<ClassSession>,
            status: LessonStatus,
        ): List<LessonWrite<LessonChange?>> {
            if (status == LessonStatus.COMPLETED) return sessions.map { setStatus(it, status) }
            val receipts = mutableListOf<SessionReceipt>()
            try {
                return sessions.map { session ->
                    val written = classes.setStatusUndoable(session, status)
                    (written as? LessonWrite.Written)?.value?.let { receipts += it }
                    written.track(settles = false)
                }
            } finally {
                settleLater(receipts)
            }
        }

        suspend fun move(session: ClassSession, start: Instant, now: Instant): LessonWrite<LessonChange?> =
            classes.moveUndoable(session, start, now).track()

        suspend fun setAttendance(klass: TutoringClass, attended: Attended): LessonWrite<LessonChange?> =
            classes.setAttendanceUndoable(klass, attended).track()

        suspend fun archive(student: Student): ArchiveChange {
            val receipts = students.archive(student)
            settleLater(receipts)
            return ArchiveChange(Assignee.Student(student.id), receipts)
        }

        suspend fun archive(group: Group): ArchiveChange {
            val receipts = groups.archive(group)
            settleLater(receipts)
            return ArchiveChange(Assignee.Group(group.id), receipts)
        }

        /** Takes [change] back only while the lesson is still as the change left it. */
        suspend fun undo(change: LessonChange): UndoResult = undoAll(listOf(change)).single()

        /** An independent guarded outcome for each change, in order; held changes share one transaction. */
        suspend fun undoAll(changes: List<LessonChange>): List<UndoResult> {
            val held = changes.mapNotNull { it.receipt }
            val restored = (if (held.isEmpty()) emptyList() else classes.undoAll(held)).iterator()
            return changes.map { change ->
                if (change.receipt != null) restored.next() else classes.undoCompletion(change.lesson)
            }
        }

        /** Restores the owner and returns how many lessons could not be restored. */
        suspend fun undoArchive(change: ArchiveChange): Int = when (val owner = change.owner) {
            is Assignee.Student -> students.unarchive(owner.studentId, change.receipts)
            is Assignee.Group -> groups.unarchive(owner.groupId, change.receipts)
        }

        private fun LessonWrite<SessionReceipt?>.track(
            settles: Boolean = true,
        ): LessonWrite<LessonChange?> = when (this) {
            is LessonWrite.Written -> LessonWrite.Written(
                value?.let {
                    if (settles) settleLater(listOf(it))
                    LessonChange.held(it)
                },
            )
            is LessonWrite.HourTaken -> LessonWrite.HourTaken(clash)
            LessonWrite.Gone -> LessonWrite.Gone
        }

        private fun settleLater(receipts: List<SessionReceipt>) {
            if (receipts.isEmpty()) return
            scope.launch {
                delay(UndoWindow)
                runCatchingCancellable { classes.settleAll(receipts) }.onFailure(onFailure)
            }
        }
    }
}
