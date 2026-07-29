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

    @Test fun `completed classes are left as history`() {
        assertEquals(Action.NONE, CalendarSyncPolicy.actionFor(LessonStatus.COMPLETED))
    }
}
