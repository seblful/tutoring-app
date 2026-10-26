package com.seblful.klas.domain.schedule

/**
 * **Why the schedule has nothing to show** — and therefore what, if anything, to offer instead.
 *
 * One answer for every view, so the views cannot drift apart — a tutor with no students must meet
 * «Добавить ученика» on «День» and «Неделя» alike, never a dead end on one of them. An empty list
 * carrying no way out of itself is the defect `EmptyState` exists to prevent. Decided here rather than
 * in composables so it can be tested.
 */
enum class ScheduleEmpty {
    /**
     * A filter is on. **Takes precedence over the other two** — a schedule narrowed to one student is not
     * a quiet one, and the way out is the chip rather than anything on the page. It offers no action
     * deliberately: «Записать занятие» here would book into the day being *looked at* and not for the
     * student being looked *for*, which is not what the tutor asked. The searchable lists follow the same
     * rule — a list empty because nothing matched says so and offers nothing.
     */
    FILTERED,

    /** Genuinely nothing booked, and something can be: the ordinary case, offering to book one. */
    QUIET,

    /**
     * Nothing booked and nothing bookable — there is no student, or no priced subject, yet. Naming the
     * emptiness and stopping there would leave the tutor to find the step that unblocks it, so this
     * offers that step instead.
     */
    NEEDS_STUDENT,
    ;

    companion object {
        /** @param filtered whether the schedule is narrowed to one student or group — see `ScheduleWho`. */
        fun of(filtered: Boolean, canAddClass: Boolean): ScheduleEmpty = when {
            filtered -> FILTERED
            canAddClass -> QUIET
            else -> NEEDS_STUDENT
        }
    }
}
