package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.lesson.CalendarSyncPolicy.Action
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncPolicyTest {

    @Test fun `scheduled classes keep an up-to-date event`() {
        assertEquals(Action.UPSERT, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.EXPECTED))
    }

    @Test fun `cancelled classes have their event removed`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.CANCELLED, Attended.EXPECTED))
    }

    /**
     * History the tutor may still correct. The note is the case this exists for — it is written on the
     * way out of the room, by which time auto-completion has usually marked the lesson taught — but the
     * hour and the title go the same way: a completed lesson the old rule answered "leave the event
     * alone" for left the calendar describing a lesson the app no longer did.
     */
    @Test fun `completed classes have their event kept true`() {
        assertEquals(Action.REFRESH, CalendarSyncPolicy.actionFor(LessonStatus.COMPLETED, Attended.PRESENT))
    }

    /**
     * **A member who is not coming is not invited.** A group lesson goes ahead without them and its own
     * status stays `SCHEDULED` — a per-member correction — so a rule that read only the status went on
     * inviting them: their event stayed in the calendar, and a re-sync (`CalendarMirror.resyncAll`) put
     * it back after they had deleted it. Archiving a student is where that was found
     * (`ClassRepository.cancelScheduledFor`); a member marked away from a lesson that has not happened
     * yet is the same answer, however they got there.
     */
    @Test fun `an absentee is withdrawn from a lesson that has not happened`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.ABSENT))
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.ABSENT_BILLED))
    }

    /**
     * And the hour they missed is still the hour the tutor taught. Once the lesson has been completed the
     * event is kept true like any other member's: the calendar is the record of the tutor's day, not a
     * bill, and a lesson that has happened is history that stops being edited by an absence.
     */
    @Test fun `an absentee's event is history once the lesson has been taught`() {
        assertEquals(Action.REFRESH, CalendarSyncPolicy.actionFor(LessonStatus.COMPLETED, Attended.ABSENT))
    }

    /**
     * The half of the old rule that was right, stated where it can be read: **only** a lesson still to
     * come may bring an event into existence. Turning sync on does not write the tutor's past into
     * their calendar, and an event they deleted from it is not resurrected by an edit to the lesson.
     */
    @Test fun `only a scheduled lesson may create an event`() {
        assertEquals(
            listOf(Action.UPSERT),
            LessonStatus.entries
                .map { CalendarSyncPolicy.actionFor(it, Attended.EXPECTED) }
                .filter { it == Action.UPSERT },
        )
    }
}
