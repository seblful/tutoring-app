package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.Subject

/** Rules for composing a valid class booking. */
object Scheduling {

    /**
     * The subjects that may be chosen when booking a class for a student: only those with a price
     * set for that student (passed in [pricedSubjectIds]). A completed class snapshots its
     * subject's price, so a class for an unpriced subject could never be billed — the price must be
     * added first. The order of [allSubjects] is preserved so the picker stays alphabetical.
     */
    fun assignableSubjects(allSubjects: List<Subject>, pricedSubjectIds: Collection<Long>): List<Subject> {
        val priced = pricedSubjectIds.toSet()
        return allSubjects.filter { it.id in priced }
    }
}
