package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Subject

/**
 * Which subject a typed name already is.
 *
 * Two «Математика» cannot be told apart anywhere they are offered — the ticks on a student, the booking
 * form's picker, a group's subject — so a name is one subject, whatever its case and the spaces around it.
 * Nothing in the table enforces that (a unique index would be a migration, and a restored backup may
 * already hold a pair); the subject repository applies this rule inside its write transaction.
 */
object SubjectNames {

    /** The subject already called [name], other than [exceptId] — the one being renamed — or null. */
    fun sameAs(name: String, subjects: List<Subject>, exceptId: Long = 0): Subject? {
        val wanted = name.trim()
        return subjects.firstOrNull { it.id != exceptId && it.name.trim().equals(wanted, ignoreCase = true) }
    }
}
