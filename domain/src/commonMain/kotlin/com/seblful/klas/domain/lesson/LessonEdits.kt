package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass

/**
 * The user-editable fields of a whole lesson — one student's or a group's. Every field is shared by
 * every class in it, [price] included: a group lesson is billed at the **group's** one hourly rate,
 * and an individual lesson is a lesson of one, so there is a single rate either way.
 */
data class SessionEdit(
    val subjectId: Long,
    val durationMinutes: Int,
    val mode: ClassMode,
    /** The hourly rate every class of the lesson is (re)billed at. */
    val price: Money,
    /**
     * Who the lesson belongs to after the edit. An **individual** class may be re-attributed to
     * another student, which credits the new owner at [price].
     *
     * Null means "leave every class with the student it already has", which is the only thing a
     * group lesson can mean — who was in one is changed by adding and removing classes
     * ([GroupRules.rosterChange]), never by relabelling one of them. [SessionRules.editsFor] refuses a
     * non-null value for a lesson of more than one class rather than trusting callers to know that.
     */
    val studentId: Long? = null,
) {
    init {
        // The same rule `Scheduling.Slot` states, at the other end of the write: this is what a lesson
        // is re-billed from, so a non-positive length would also charge nothing for it.
        require(durationMinutes > 0) { "A lesson lasts a positive number of minutes, not $durationMinutes" }
    }
}

/**
 * What a lesson's roster change comes to: [dropped] are the classes of the members leaving it,
 * [joining] whole new classes for the ones arriving. A joiner is built unbilled — writing it is what
 * settles the charge (see [ClassRules.rebill]).
 */
data class RosterChange(
    val dropped: List<TutoringClass> = emptyList(),
    val joining: List<TutoringClass> = emptyList(),
) {
    /**
     * The leavers whose class had already billed. Removing those takes income the student was
     * charged with them, so it is worth asking about first — the same warning deleting a completed
     * lesson owes.
     */
    val billedDropped: List<TutoringClass> get() = Billing.billable(dropped)
}

/**
 * One lesson edit, resolved whole: [lesson] carries the **lesson row's** own fields afterwards — its
 * hour, length, subject, mode and status — and the three lists are what becomes of the people in it.
 * [updated] stay and are rewritten in place, [joining] arrive with no row yet, [dropped] leave.
 *
 * **[lesson] is its own field rather than "the first of [updated]", and that is the whole point of the
 * type.** A roster can be swapped *whole* — every member unticked, different ones ticked — and then
 * there is no updated class left to read the lesson's new hour off. The write that projected the lesson
 * row out of that list therefore stored the roster and silently kept the **old time**, while the hour
 * check had already reserved the new one and the calendar had already mirrored it there; the joiners
 * were re-billed from the new length against the stored old one, so a day's takings and the report
 * disagreed. Any class of the lesson is a truthful carrier of these fields (they are all shared, and
 * [ClassRules.edit] derives the status from the move alone), so there is always one to name — which is
 * exactly why leaving it implied was possible.
 */
data class LessonEdit(
    val lesson: TutoringClass,
    val updated: List<TutoringClass>,
    val joining: List<TutoringClass> = emptyList(),
    val dropped: List<TutoringClass> = emptyList(),
)
