package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus

/**
 * Decides how a class's calendar event should mirror its state — its status **and** whether this member
 * is coming. Kept pure so the rule is unit-testable independently of any calendar backend.
 *
 * - A class that will still happen ([LessonStatus.SCHEDULED]) should have an up-to-date event.
 * - A class that won't happen should have none, and that is two different things: the lesson was called
 *   off ([LessonStatus.CANCELLED]), or only this member is not coming ([Attended.ABSENT]) while the
 *   lesson goes ahead for everybody else. An event is an invitation and a reminder, and nobody is being
 *   invited to an hour they will not be at.
 * - A [LessonStatus.COMPLETED] class is history — and history the tutor is still allowed to correct,
 *   so its event is kept true without one ever being created for it ([Action.REFRESH]).
 */
object CalendarSyncPolicy {

    /**
     * What a mirror owes an event.
     *
     * [UPSERT] and [REFRESH] differ in exactly one thing — whether an event may be *brought into
     * existence* — and that is the whole distinction between a lesson still to come and one already
     * taught. Both write the same fields to an event that exists.
     */
    enum class Action {
        /** Create the event if there is none, then write the lesson's current fields to it. */
        UPSERT,

        /** Write the lesson's current fields to the event **if it still exists**. Create nothing. */
        REFRESH,

        /** Remove the event and forget its id. */
        DELETE,
    }

    /**
     * Completed lessons refresh existing events but never create them. Cancelled lessons and absent
     * members of scheduled lessons have no event; other scheduled lessons are upserted. Attendance does
     * not affect a completed lesson's event.
     */
    fun actionFor(status: LessonStatus, attended: Attended): Action = when {
        status == LessonStatus.COMPLETED -> Action.REFRESH
        status == LessonStatus.CANCELLED -> Action.DELETE
        attended == Attended.ABSENT || attended == Attended.ABSENT_BILLED -> Action.DELETE
        else -> Action.UPSERT
    }
}
