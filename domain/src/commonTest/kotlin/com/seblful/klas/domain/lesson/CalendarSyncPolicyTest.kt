package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.lesson.CalendarSyncPolicy.Action
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarSyncPolicyTest {

    @Test fun `scheduled classes keep an up-to-date event`() {
        assertEquals(Action.UPSERT, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.EXPECTED))
    }

    @Test fun `cancelled classes have their event removed`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.CANCELLED, Attended.EXPECTED))
    }

    @Test fun `completed classes have their event kept true`() {
        assertEquals(Action.REFRESH, CalendarSyncPolicy.actionFor(LessonStatus.COMPLETED, Attended.PRESENT))
    }

    @Test fun `an absentee is withdrawn from a lesson that has not happened`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.ABSENT))
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(LessonStatus.SCHEDULED, Attended.ABSENT_BILLED))
    }

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
