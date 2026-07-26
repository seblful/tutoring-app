package com.seblful.klas.domain

import com.seblful.klas.domain.model.TutoringClass

/**
 * Which of the two kinds of teaching a lesson is: one-to-one, or one of a group's.
 *
 * The distinction has always been in the data — a class either names a group or it doesn't — but it
 * was only ever read as a `groupId != null` check at the point of use, so each screen decided for
 * itself whether to mention it and most of them said nothing at all. That left the two kinds
 * indistinguishable exactly where they mix: a student's lesson list, and the income totals. Naming
 * the kind gives the app one vocabulary for it, and makes "which kind is this" a value that can be
 * shown, filtered and summed rather than a condition re-derived per composable.
 *
 * It reads the **class**, never a [ClassSession], on purpose: `ClassSession.of` deliberately makes a
 * session of one out of a single member's class (that is how a per-member correction stays
 * expressible), and such a session carries no group id though the class plainly belongs to a group
 * lesson. The class's own `groupId` is the storage truth and cannot disagree with itself.
 */
enum class LessonKind {

    /** One student, billed at their own price for the subject. */
    INDIVIDUAL,

    /** One of a group's lessons, billed at the group's rate — see `Group.price`. */
    GROUP;

    companion object {
        fun of(klass: TutoringClass): LessonKind = if (klass.groupId != null) GROUP else INDIVIDUAL

        /** How many classes of each kind [classes] holds; a kind with none is **absent**, not zero. */
        fun tally(classes: List<TutoringClass>): Map<LessonKind, Int> =
            classes.groupingBy { of(it) }.eachCount()
    }
}

/**
 * Which kinds a lesson list is showing — the narrowing offered above a list that holds both.
 *
 * [ALL] is a real option rather than the absence of a filter, so the choice is always visible: a
 * student who has both kinds should be able to see that from the list itself, and one who has only
 * individual lessons should not be left wondering whether something is being hidden.
 */
enum class LessonFilter {
    ALL,
    INDIVIDUAL,
    GROUP;

    fun accepts(kind: LessonKind): Boolean = when (this) {
        ALL -> true
        INDIVIDUAL -> kind == LessonKind.INDIVIDUAL
        GROUP -> kind == LessonKind.GROUP
    }

    fun apply(classes: List<TutoringClass>): List<TutoringClass> =
        classes.filter { accepts(LessonKind.of(it)) }

    companion object {
        /** The filter for one kind — what tapping a kind's own chip selects. */
        fun of(kind: LessonKind): LessonFilter = when (kind) {
            LessonKind.INDIVIDUAL -> INDIVIDUAL
            LessonKind.GROUP -> GROUP
        }
    }
}
