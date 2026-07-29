package com.seblful.klas.domain

import com.seblful.klas.domain.model.LessonStatus

/**
 * Decides how a class's calendar event should mirror its status. Kept pure so the rule is
 * unit-testable independently of any calendar backend.
 *
 * - A class that will still happen ([LessonStatus.SCHEDULED]) should have an up-to-date event.
 * - A class that won't happen ([LessonStatus.CANCELLED]) should have none.
 * - A [LessonStatus.COMPLETED] class is history; its (past) event is left untouched.
 */
object CalendarSyncPolicy {

    enum class Action { UPSERT, DELETE, NONE }

    fun actionFor(status: LessonStatus): Action = when (status) {
        LessonStatus.SCHEDULED -> Action.UPSERT
        LessonStatus.CANCELLED -> Action.DELETE
        LessonStatus.COMPLETED -> Action.NONE
    }
}
