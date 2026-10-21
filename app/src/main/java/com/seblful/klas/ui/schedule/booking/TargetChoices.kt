package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student

/** Somebody the booking form offers to book for: a student or a group, and the name it shows. */
data class TargetChoice(val who: Assignee, val name: String)

/** Who a new booking offers, in its «Who» list. */
object TargetChoices {
    /** How many of the most recently taught lead the list — enough for a tutor's regulars. */
    const val SHOWN = 4

    /**
     * The people most recently taught, then — so a tutor with little history still gets chips — the rest
     * of the students and groups in list order; archived ones never. Whoever the form has [chosen] is
     * always among them, first if it had to be added.
     */
    fun pick(
        recent: List<Assignee>,
        students: List<Student>,
        groups: List<Group>,
        chosen: Assignee? = null,
        shown: Int = SHOWN,
    ): List<TargetChoice> {
        val named = students.filterNot { it.archived }.map { TargetChoice(Assignee.Student(it.id), it.name) } +
            groups.filterNot { it.archived }.map { TargetChoice(Assignee.Group(it.id), it.name) }
        val byWho = named.associateBy { it.who }
        val ordered = (recent.mapNotNull { byWho[it] } + named).distinct()
        val picked = ordered.take(shown)
        val selected = chosen?.let { byWho[it] } ?: return picked
        return if (selected in picked) picked else listOf(selected) + picked.dropLast(1)
    }
}

/** Who [ClassForm] is booking for, or null while it has nobody. */
internal val ClassForm.assignee: Assignee?
    get() = when (target) {
        ClassTarget.STUDENT -> studentId?.let(Assignee::Student)
        ClassTarget.GROUP -> groupId?.let(Assignee::Group)
    }
