package com.seblful.klas.domain.model

import kotlin.time.Instant

/**
 * Who a piece of [Homework] is for: one student, or a whole group.
 *
 * The two cases a lesson has, for the same reason — a group is set one assignment and marked done once,
 * not handed a copy per member that could drift apart. Sealed, so "both" and "neither" cannot be written;
 * the flat nullable pair that stores it lives only on `HomeworkEntity`, and `Mappers.kt` is the one place
 * it is read back, as [RateOwner] is for a rate.
 */
sealed interface Assignee {
    data class Student(val studentId: Long) : Assignee

    data class Group(val groupId: Long) : Assignee
}

/**
 * One piece of homework: what was set, for whom, and whether it is done.
 *
 * **Done or not, and nothing in between**: it is current until it is swiped done, and done is the archive.
 * There is no per-student verdict.
 *
 * @param givenAt when it was set — what "the next lesson" is counted from. The lesson it is attached to is
 *   derived from this, never stored (`domain/homework/HomeworkRules`).
 * @param doneAt when it was marked done, or null while it is current.
 */
data class Homework(
    val id: Long = 0,
    val assignee: Assignee,
    val text: String,
    val givenAt: Instant,
    val doneAt: Instant? = null,
) {
    val isDone: Boolean get() = doneAt != null
}
