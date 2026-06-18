package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.Subject

/** Rules for composing a valid class booking. */
object Scheduling {

    /**
     * The subjects that may be chosen when booking a class for a student: only the ones the
     * student is assigned. A price exists only for an assigned subject, and a completed class
     * snapshots that price — so offering an unassigned subject would mean a class that can never
     * be billed. The order of [allSubjects] is preserved so the picker stays alphabetical.
     */
    fun assignableSubjects(allSubjects: List<Subject>, assignedSubjectIds: Collection<Long>): List<Subject> {
        val assigned = assignedSubjectIds.toSet()
        return allSubjects.filter { it.id in assigned }
    }
}
