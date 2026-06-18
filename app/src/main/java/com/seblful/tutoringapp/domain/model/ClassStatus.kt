package com.seblful.tutoringapp.domain.model

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
}
