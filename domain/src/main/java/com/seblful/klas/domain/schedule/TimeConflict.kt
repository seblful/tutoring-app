package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.reminders.Reminders

/**
 * A lesson already in the schedule at the time a form or a move is pointing at. There is one tutor,
 * so this is a refusal rather than a warning: [who] (the group's name, or the student's) and [slot]
 * say which lesson is in the way, so the tutor can pick another time or move that one.
 */
data class TimeConflict(val slot: Scheduling.Slot, val who: String) {
    companion object {
        /**
         * Names the lesson standing in the way by the rule a reminder is announced by
         * ([Reminders.announcedBy]): its group when it is a group lesson, otherwise the student whose class
         * it is — and the student too when the group's name is blank, which an `?:` let through as a
         * nameless refusal. Blank when neither is known, which leaves the message saying *when* it clashes
         * — better than not refusing at all.
         */
        fun of(clash: ClassSession, groupName: String?, studentName: String?): TimeConflict =
            TimeConflict(Scheduling.slotOf(clash.first), Reminders.announcedBy(groupName, studentName))
    }
}
