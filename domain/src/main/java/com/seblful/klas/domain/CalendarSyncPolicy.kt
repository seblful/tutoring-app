package com.seblful.klas.domain

import com.seblful.klas.domain.model.LessonStatus

/**
 * Decides how a class's calendar event should mirror its status. Kept pure so the rule is
 * unit-testable independently of any calendar backend.
 *
 * - A class that will still happen ([LessonStatus.SCHEDULED]) should have an up-to-date event.
 * - A class that won't happen ([LessonStatus.CANCELLED]) should have none.
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
     */
    fun actionFor(status: LessonStatus): Action = when (status) {
        LessonStatus.SCHEDULED -> Action.UPSERT
        LessonStatus.COMPLETED -> Action.REFRESH
        LessonStatus.CANCELLED -> Action.DELETE
    }
}
