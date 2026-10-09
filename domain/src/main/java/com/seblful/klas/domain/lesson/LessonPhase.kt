package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * Where a lesson stands **at a given instant** — its stored [LessonStatus] read against the clock.
 *
 * ### Why "in progress" is not a fourth status
 *
 * A lesson that has started and not yet ended is a fact about the time of day, not a fact the tutor
 * records. Storing it would need something to write it — an alarm at each start, or the upkeep pass —
 * and that machinery can only ever be *late*: a phone switched off over the lesson, a Doze window, a
 * boot in the middle of the hour, and the row says «Запланировано» while the tutor is in the room, or
 * «Идёт» the next morning. Derived, it cannot be wrong and cannot get stuck, which is the same argument
 * the app already makes for balances and for `NowMarker`: what the clock can answer is not written down.
 *
 * It also keeps `LessonStatus` the set of things that are *decided* — and so keeps the transition table,
 * the billing rule, the backup format and `StoredEnumNamesTest` unchanged.
 *
 * ### Why [OVERDUE] is here and not only in the upkeep pass
 *
 * "The lesson has finished and nobody has said what became of it" was written twice: once as
 * `ClassRules.shouldAutoComplete` and once as whatever the evening digest would have counted. They are
 * the same sentence, and two copies of it could disagree about the boundary — which would mean the
 * notification naming lessons auto-completion had already settled, or missing ones it had not. Both read
 * this now.
 *
 * ### The boundaries
 *
 * Half-open, `[start, end)`, matching `Scheduling.Slot`: a lesson is in progress from its first second
 * and over at its last, so back-to-back lessons never both count as running. A stored row with
 * `start == end` — which no write can produce — is never [IN_PROGRESS], which is the safe way round.
 */
enum class LessonPhase {

    /** Still to come: scheduled, and its start has not arrived. */
    UPCOMING,

    /** Happening now: scheduled, started, not yet ended. This is what the schedule badges «Идёт». */
    IN_PROGRESS,

    /**
     * Finished, and still scheduled — **nobody has said whether it happened.**
     *
     * What auto-completion settles when it is on (`ClassRules.shouldAutoComplete`), and what the evening
     * digest counts when it is off (`Digests.Unfinished`). Those are the two halves of one question: an
     * app that can see the clock but not the room either assumes the lesson happened or asks.
     */
    OVERDUE,

    /**
     * The tutor has said what became of it — completed or cancelled — so the clock has nothing to add.
     *
     * One arm for both, deliberately: this type answers *where now falls*, and for a lesson with a
     * terminal status the answer is "nowhere". Which terminal status it is remains [LessonStatus]'s to
     * say, and every reader that cares already has the lesson.
     */
    SETTLED,
    ;

    /** Whether the tutor is in this lesson right now. */
    val isRunning: Boolean get() = this == IN_PROGRESS

    /** Whether this lesson is over and unaccounted for — see [OVERDUE]. */
    val isUnfinished: Boolean get() = this == OVERDUE

    companion object {

        /**
         * The phase of a lesson with [status] running from [start] to [end], at [now].
         *
         * Takes the four facts rather than a class, on the rule that keeps this package free of anything
         * larger than it needs — and so that a caller holding only a row's columns can still ask.
         */
        fun of(status: LessonStatus, start: Instant, end: Instant, now: Instant): LessonPhase = when {
            status != LessonStatus.SCHEDULED -> SETTLED
            now.isBefore(start) -> UPCOMING
            now.isBefore(end) -> IN_PROGRESS
            else -> OVERDUE
        }

        /** [of], for one class — its lesson's hour and status are on it. */
        fun of(klass: TutoringClass, now: Instant): LessonPhase =
            of(klass.status, klass.start, klass.end, now)

        /**
         * [of], for a whole lesson. Every class of one shares its hour and its status
         * ([ClassSession.first] carries both), so a session has exactly one phase.
         */
        fun of(session: ClassSession, now: Instant): LessonPhase = of(session.first, now)
    }
}
