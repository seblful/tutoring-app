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
     * A completed lesson used to answer "leave it alone entirely", and that was wrong in the one way
     * that matters most: **a note is usually written after the lesson**, not before it. The tutor walks
     * out of the room, opens the lesson — which auto-completion has very likely already marked as taught
     * — and writes down what was set for home. Under the old rule that note, and any correction to the
     * hour or the title beside it, reached the database and stopped there, so the calendar went on
     * showing a lesson the app no longer described.
     *
     * What [Action.REFRESH] keeps of the old rule is the half that was right: **no event is created for
     * a lesson that has already happened.** Turning sync on does not backfill the tutor's past into
     * their calendar, and an event they deleted from it themselves is not resurrected by an edit.
     *
     * **Attendance is read here too, not only the status.** A group lesson goes ahead without a member
     * who is not coming: that is a per-member correction and the lesson's own status stays [SCHEDULED],
     * so a rule that read only the status went on inviting them — their event stayed in the calendar, and
     * a re-sync (`CalendarMirror.resyncAll`) put it back after they had deleted it. Archiving a student
     * is where that was found (`ClassRepository.cancelScheduledFor`), but it is the same answer for a
     * member marked away from a lesson that has not happened yet, however they got there.
     *
     * Once the lesson has been **taught**, an absence stops making that call: the hour happened, the
     * calendar is the record of the tutor's day rather than a bill, and the event is treated like any
     * other member's — kept true, never created.
     */
    fun actionFor(status: LessonStatus, attended: Attended): Action = when {
        status == LessonStatus.COMPLETED -> Action.REFRESH
        status == LessonStatus.CANCELLED -> Action.DELETE
        attended == Attended.ABSENT || attended == Attended.ABSENT_BILLED -> Action.DELETE
        else -> Action.UPSERT
    }
}
