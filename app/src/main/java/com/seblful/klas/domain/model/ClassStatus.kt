package com.seblful.klas.domain.model

/**
 * Lifecycle of a class.
 *
 * `scheduled` is the only non-terminal state. A class auto-completes once its end
 * time passes (the app cannot detect attendance, only the clock). If the class did
 * not actually happen, the tutor cancels it, which voids the charge.
 *
 * Note: "moving" a class is modelled as an in-place reschedule (the class keeps the
 * `SCHEDULED` status with a new start time), not a separate terminal state — a moved
 * class is still a class that will happen.
 */
enum class ClassStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED;

    val isBillable: Boolean get() = this == COMPLETED

    /**
     * Whether the class takes up the tutor's time, and so stands in the way of another lesson at the
     * same hour (see `Scheduling.conflicts`). A cancelled class did not happen, so it gives its slot
     * back; a completed one did, so nothing else can be recorded as happening then.
     */
    val occupiesTime: Boolean get() = this != CANCELLED

    companion object {
        /**
         * The status stored as [stored], or **null** when that is not one of these names.
         *
         * A total function, unlike `valueOf`, because the caller is always reading somebody else's
         * text: the `classes.status` column, or a backup file's copy of it. `valueOf` throws, and it
         * throws on the *read* path — inside a `Flow` the schedule collects — so one unrecognized row
         * used to take down every screen that lists a lesson, permanently and with no way back short of
         * clearing the app's data. Returning null lets each caller decide, and there are only two
         * callers: the import refuses the file (`BackupIntegrity`), and the mapper fails loudly on a
         * row the import can no longer have let through.
         */
        fun parse(stored: String): ClassStatus? = entries.firstOrNull { it.name == stored }
    }
}
