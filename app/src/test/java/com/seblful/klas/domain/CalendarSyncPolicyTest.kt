package com.seblful.klas.domain

import com.seblful.klas.domain.CalendarSyncPolicy.Action
import com.seblful.klas.domain.model.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncPolicyTest {

    @Test fun `scheduled classes keep an up-to-date event`() {
        assertEquals(Action.UPSERT, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED))
    }

    @Test fun `cancelled classes have their event removed`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.CANCELLED))
    }

    /**
     * History the tutor may still correct. The note is the case this exists for — it is written on the
     * way out of the room, by which time auto-completion has usually marked the lesson taught — but the
     * hour and the title go the same way: a completed lesson that answered [Action.NONE] left the
     * calendar describing a lesson the app no longer did.
     */
    @Test fun `completed classes have their event kept true`() {
        assertEquals(Action.REFRESH, CalendarSyncPolicy.actionFor(LessonStatus.COMPLETED))
    }

    /**
     * The half of the old rule that was right, stated where it can be read: **only** a lesson still to
     * come may bring an event into existence. Turning sync on does not write the tutor's past into
     * their calendar, and an event they deleted from it is not resurrected by an edit to the lesson.
     */
    @Test fun `only a scheduled lesson may create an event`() {
        assertEquals(
            listOf(Action.UPSERT),
            LessonStatus.entries.map(CalendarSyncPolicy::actionFor).filter { it == Action.UPSERT },
        )
    }
}
