package com.seblful.klas.domain.model

/**
 * Lifecycle of a **lesson** — the hour, not the people in it.
 *
 * `SCHEDULED` is the only non-terminal state. A lesson auto-completes once its end time passes (the app
 * cannot detect attendance, only the clock). If it did not happen at all, the tutor cancels it, which
 * voids what it billed.
 *
 * Note: "moving" a lesson is modelled as an in-place reschedule (it keeps `SCHEDULED` with a new start
 * time), not a separate terminal state — a moved lesson is still a lesson that will happen.
 *
 * **Whether one student was there is [Attended], not this.** The two used to be one enum on one row per
 * student, so a group lesson whose absentee was cancelled had no status of its own and had to derive one
 * by precedence; a no-show could only be recorded by voiding that student's charge, and "called off for
 * everyone" was the same rows as "nobody came".
 */
enum class LessonStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED;

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
         * text: the `lessons.status` column, or a backup file's copy of it. `valueOf` throws, and it
         * throws on the *read* path — inside a `Flow` the schedule collects — so one unrecognized row
         * used to take down every screen that lists a lesson, permanently and with no way back short of
         * clearing the app's data. Returning null lets each caller decide, and there are only two
         * callers: the import refuses the file (`BackupIntegrity`), and the mapper fails loudly on a
         * row the import can no longer have let through.
         */
        fun parse(stored: String): LessonStatus? = entries.firstOrNull { it.name == stored }

        /**
         * The order a lesson's status is decided in when **only its members' statuses are known**:
         * scheduled if any member still is, else completed if any is, else cancelled. Nothing in the app
         * derives a status this way any more — the lesson holds its own — except the two readers of the
         * *pre-v2* model, where it was the only answer there was: `MIGRATION_1_2` in SQL and
         * `splitIntoLessons` over a v1–v5 backup file.
         *
         * **Stated as a list because it was the enum's declaration order.** `entries.firstOrNull { … }`
         * reproduced the SQL only by accident of how the constants happen to be written down, and
         * `StoredEnumNamesTest` — the acknowledgement list built for exactly "renaming one of these is a
         * migration" — compares them as a **set**, so it is order-insensitive by construction. Reordering
         * an enum is an ordinary refactor that no test here would have failed, and it would have made
         * every old backup import a mixed group lesson as **completed**: income the app never recognised,
         * arriving silently, on the one path a tutor reaches for after losing a phone.
         */
        val PRECEDENCE: List<LessonStatus> = listOf(SCHEDULED, COMPLETED, CANCELLED)
    }
}

/**
 * Whether one student was in one lesson — their `attendance` row's state.
 *
 * The lesson says whether the hour happened ([LessonStatus]); this says who was there for it, and it is
 * the reason both can now be true at once: a lesson taught with somebody missing, rather than a lesson
 * with no status.
 *
 * [ABSENT_BILLED] is the state the old model could not hold at all. A student who does not turn up under
 * a late-cancellation policy still owes for the hour, and the only way to express that before was to
 * record them as having been there.
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
    ABSENT_BILLED;

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
