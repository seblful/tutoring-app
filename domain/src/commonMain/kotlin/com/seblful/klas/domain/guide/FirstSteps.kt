package com.seblful.klas.domain.guide

/** The deeds a new tutor does once to have used the app end to end, in the order a first week brings them. */
enum class FirstStep { BOOK, MARK_TAUGHT, PAYMENT, HOMEWORK, REMINDERS }

/**
 * Which [FirstStep]s are done. Each is ticked by the deed itself, read from the data — a lesson booked, one
 * taught, a payment, some homework, reminders that reach the phone — never by a flag set when a button was
 * tapped, so it cannot say «done» about something that was undone, nor miss one done another way.
 */
data class FirstSteps(val done: Set<FirstStep>) {

    val complete: Boolean get() = done.containsAll(FirstStep.entries)

    /** The first step still to do, which the card leads with; null once all are. */
    val next: FirstStep? get() = FirstStep.entries.firstOrNull { it !in done }

    /** What the data says has happened at least once. */
    data class Progress(
        val booked: Boolean = false,
        val taught: Boolean = false,
        val paid: Boolean = false,
        val homework: Boolean = false,
    )

    companion object {
        /** @param reminded whether lesson reminders are on and the phone lets them through. */
        fun of(progress: Progress, reminded: Boolean): FirstSteps = FirstSteps(
            buildSet {
                if (progress.booked) add(FirstStep.BOOK)
                if (progress.taught) add(FirstStep.MARK_TAUGHT)
                if (progress.paid) add(FirstStep.PAYMENT)
                if (progress.homework) add(FirstStep.HOMEWORK)
                if (reminded) add(FirstStep.REMINDERS)
            },
        )
    }
}
