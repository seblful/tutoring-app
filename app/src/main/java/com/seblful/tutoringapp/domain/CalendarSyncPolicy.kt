package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus

/**
 * Decides how a class's calendar event should mirror its status. Kept pure so the rule is
 * unit-testable independently of any calendar backend.
 *
 * - A class that will still happen ([ClassStatus.SCHEDULED]) should have an up-to-date event.
 * - A class that won't happen ([ClassStatus.CANCELLED]/[ClassStatus.MISSED]) should have none.
 * - A [ClassStatus.COMPLETED] class is history; its (past) event is left untouched.
 */
object CalendarSyncPolicy {

    enum class Action { UPSERT, DELETE, NONE }

    fun actionFor(status: ClassStatus): Action = when (status) {
        ClassStatus.SCHEDULED -> Action.UPSERT
        ClassStatus.CANCELLED, ClassStatus.MISSED -> Action.DELETE
        ClassStatus.COMPLETED -> Action.NONE
    }
}
