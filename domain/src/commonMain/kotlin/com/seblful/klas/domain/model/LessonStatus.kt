package com.seblful.klas.domain.model

import com.seblful.klas.domain.lesson.ClassRules

/**
 * Lifecycle of a **lesson** — the hour, not the people in it.
 *
 * A lesson is scheduled, completed, or cancelled. Auto-completion is an opt-in setting; the app cannot
 * detect attendance. Transitions and billing rules live in [ClassRules].
 *
 * Rescheduling changes the lesson's start time and may change its status; see [ClassRules.move].
 *
 * **Whether one student was there is [Attended], not this**: a group lesson whose absentee is cancelled
 * still has a status of its own, a no-show is recorded without voiding that student's charge, and
 * "called off for everyone" is not the same as "nobody came".
 */
enum class LessonStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    ;

    /** Whether the lesson happened, and so whether anybody in it can owe for it (see [Attended.bills]). */
    val isBillable: Boolean get() = this == COMPLETED

    /**
     * Whether the lesson takes up the tutor's time, and so stands in the way of another at the same hour
     * (see `Scheduling.conflicts`). A cancelled lesson did not happen, so it gives its slot back; a
     * completed one did, so nothing else can be recorded as happening then.
     */
    val occupiesTime: Boolean get() = this != CANCELLED

    companion object {
        /**
         * The status stored as [stored], or **null** when that is not one of these names.
         *
         * A total function, unlike `valueOf`, because the caller is always reading somebody else's
         * text: the `lessons.status` column. `valueOf` throws, and it throws on the *read* path — inside a
         * `Flow` the schedule collects — so one unrecognized row would take down every screen that
         * lists a lesson, permanently and with no way back short of clearing the app's data. Returning
         * null lets the caller decide: `EnumConverters` fails loudly, naming the text, on a row a backup
         * cannot have let through (a file naming an unknown status is refused as it is parsed).
         */
        fun parse(stored: String): LessonStatus? = entries.firstOrNull { it.name == stored }

        /**
         * The order a lesson's status is decided in when **only its members' statuses are known**:
         * scheduled if any member still is, else completed if any is, else cancelled. The lesson holds its
         * own status, so only the reader of the *pre-v2* model uses this: `splitIntoLessons`, over a v1–v5
         * backup **file**, which outlives the schema it came from.
         *
         * **Stated as a list, not read off the enum's declaration order.** `StoredEnumNamesTest` — the
         * acknowledgement list built for exactly "renaming one of these is a migration" — compares names
         * as a **set**, so it is order-insensitive by construction. Reordering an enum is an ordinary
         * refactor that no test here would fail, and it would make every old backup import a mixed group
         * lesson as **completed**: income the app never recognised, arriving silently, on the one path a
         * tutor reaches for after losing a phone.
         */
        val PRECEDENCE: List<LessonStatus> = listOf(SCHEDULED, COMPLETED, CANCELLED)
    }
}

/**
 * Whether one student was in one lesson — their `attendance` row's state.
 *
 * The lesson says whether the hour happened ([LessonStatus]); this says who was there for it, so a
 * lesson can be taught with somebody missing.
 *
 * [ABSENT_BILLED] is for a student who does not turn up under a late-cancellation policy and still owes
 * for the hour: it records the absence without recording them as having been there.
 */
enum class Attended {
    /** The default while a lesson is still to come: they are meant to be there. */
    EXPECTED,

    /** They were there. */
    PRESENT,

    /** They were not, and are not charged for it. */
    ABSENT,

    /**
     * They were not, and owe for it anyway — a late cancellation.
     *
     * It bills only in a lesson that **happened** ([LessonStatus.isBillable]), which is what a
     * one-to-one late cancellation is: the hour was the tutor's, held and unusable, so the lesson is
     * completed and the student it was for is marked absent-and-billed. A lesson genuinely called off
     * bills nobody, whatever their attendance says.
     */
    ABSENT_BILLED,

    ;

    /**
     * Whether this student owes for the lesson **if the lesson happened** — the other half of the
     * question is [LessonStatus.isBillable], and `TutoringClass.isBillable` is where the two meet. A
     * lesson still to come bills nobody, however its attendance reads.
     *
     * **Only an explicit absence stops the charge.** [EXPECTED] means nothing was recorded, and for a
     * lesson marked as having happened the app's answer to that has always been that it happened for
     * everyone in it — it cannot see who was in the room, only the clock (`ClassRules.shouldAutoComplete`).
     * So the tutor's one tap on «Проведено» still bills the whole room, and marking somebody [ABSENT] is
     * the deliberate act that takes theirs off.
     */
    val bills: Boolean get() = this != ABSENT

    /** Whether they were actually in the room — what "N of M came" counts. */
    val wasThere: Boolean get() = this == PRESENT

    companion object {
        /** As [LessonStatus.parse], for the `attendance.state` column. */
        fun parse(stored: String): Attended? = entries.firstOrNull { it.name == stored }
    }
}
