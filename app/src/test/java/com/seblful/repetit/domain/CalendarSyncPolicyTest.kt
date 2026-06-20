package com.seblful.repetit.domain

import com.seblful.repetit.domain.CalendarSyncPolicy.Action
import com.seblful.repetit.domain.model.ClassStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncPolicyTest {

    @Test fun `scheduled classes keep an up-to-date event`() {
        assertEquals(Action.UPSERT, CalendarSyncPolicy.actionFor(ClassStatus.SCHEDULED))
    }

    @Test fun `cancelled classes have their event removed`() {
        assertEquals(Action.DELETE, CalendarSyncPolicy.actionFor(ClassStatus.CANCELLED))
    }

    @Test fun `completed classes are left as history`() {
        assertEquals(Action.NONE, CalendarSyncPolicy.actionFor(ClassStatus.COMPLETED))
    }
}
