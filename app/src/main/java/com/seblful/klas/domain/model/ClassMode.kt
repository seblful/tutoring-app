package com.seblful.klas.domain.model

/**
 * Where a class is held: in person («Очно») or remotely («Удалённо»).
 *
 * Each [Student] has a [Student.defaultMode]; a new class starts from it but can be changed
 * per occurrence (see [TutoringClass.mode]).
 */
enum class ClassMode {
    IN_PERSON,
    REMOTE;

    companion object {
        /**
         * The mode stored as [stored], or **null** when that is not one of these names. Total for the
         * reason [ClassStatus.parse] gives — this is read from three text columns
         * (`classes.mode`, `students.defaultMode`, `student_groups.defaultMode`) and from a backup
         * file's copy of each.
         *
         * Unlike a status, a mode carries no money, which is why the mapper is allowed to fall back to
         * [IN_PERSON] rather than refuse: the cost is one lesson labelled «Очно» when it was remote,
         * against a screen that will not open at all.
         */
        fun parse(stored: String): ClassMode? = entries.firstOrNull { it.name == stored }
    }
}
