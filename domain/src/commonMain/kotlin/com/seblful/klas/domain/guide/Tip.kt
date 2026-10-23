package com.seblful.klas.domain.guide

/**
 * A gesture no screen can show — a swipe, a long-press, a title that is also a button — said once, on the
 * thing it works on, the first time that thing is in reach. What can be seen (a labelled button, an empty
 * page's way out) is never a tip.
 *
 * The names are stored: renaming one shows its tip again to everyone who has seen it.
 */
enum class Tip {
    /** The schedule's page title opens a date picker. */
    SCHEDULE_DATE,

    /** A lesson is swiped to mark it taught. */
    LESSON_SWIPE,

    /** A long-press picks several lessons, to mark or cancel together. */
    LESSON_PICK,

    /** Current homework is swiped to mark it done. */
    HOMEWORK_SWIPE,
    ;

    companion object {
        /**
         * The one tip the schedule may show: the gesture nearest to use first — the swipe while a lesson on
         * screen can take it, picking once there are two to pick — then the date title, which is always there.
         */
        fun forSchedule(seen: Set<Tip>, swipeable: Boolean, lessons: Int): Tip? = listOfNotNull(
            LESSON_SWIPE.takeIf { swipeable },
            LESSON_PICK.takeIf { lessons >= 2 },
            SCHEDULE_DATE,
        ).firstOrNull { it !in seen }

        /** The homework tab's one tip, once there is current homework to swipe. */
        fun forHomework(seen: Set<Tip>, current: Int): Tip? = HOMEWORK_SWIPE.takeIf { current > 0 && it !in seen }
    }
}

/**
 * Where a tutor stands with the guidance a new install gets: which [Tip]s they have seen, and whether they
 * have put the first-steps card away.
 */
data class Guide(val seen: Set<Tip> = emptySet(), val firstStepsHidden: Boolean = false) {

    fun seeing(tip: Tip): Guide = copy(seen = seen + tip)

    companion object {
        /** Just through setup: everything still to show — and what Settings' «Show tips again» goes back to. */
        val FRESH = Guide()

        /**
         * Someone who already knows the app: they restored a backup, or used it before there was a guide. A tip
         * added later is not in [seen] and is still shown to them once.
         */
        val DONE = Guide(Tip.entries.toSet(), firstStepsHidden = true)
    }
}
