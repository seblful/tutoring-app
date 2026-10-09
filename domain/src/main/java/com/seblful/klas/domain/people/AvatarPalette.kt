package com.seblful.klas.domain.people

/**
 * Which slot of the avatar palette a student gets.
 *
 * Keyed on the student's **id**, not their name. Hashing the name gave two unrelated students the
 * same colour far more often than "1 in [size]" suggests — it is the birthday problem, so a tutor
 * with a handful of students is already likely to see a repeat — and it also moved a student to a
 * different colour when a typo in their name was fixed. Room ids are sequential, so keying on them
 * hands the first [size] students one distinct colour each, stable across renames and across a
 * backup restore (which preserves ids).
 *
 * Beyond [size] students the palette necessarily wraps; ids that far apart are rarely on screen
 * together, which is the best a fixed palette can do.
 */
object AvatarPalette {

    /** The palette index for [studentId], always within `0 until size`. */
    fun indexFor(studentId: Long, size: Int): Int {
        require(size > 0) { "palette must have at least one colour" }
        // Normalised for negatives: Kotlin's % keeps the sign of the dividend.
        return (((studentId % size) + size) % size).toInt()
    }
}
