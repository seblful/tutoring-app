package com.seblful.klas.domain.guide

/**
 * What a restored backup cannot bring, being the phone's rather than the data's: its permission to remind, its
 * calendar, the folder the weekly copy goes to. Each is asked for again only if the phone the backup came from had
 * it on.
 */
enum class PhoneStep { REMINDERS, CALENDAR, WEEKLY_COPY }

/**
 * Which of the [wanted] [PhoneStep]s this phone has. Each is read from the phone's own state — reminders that reach
 * it, a calendar mirrored, a folder picked — never from a tap on its row.
 */
data class PhoneSetup(val wanted: Set<PhoneStep>, val done: Set<PhoneStep>) {

    /** The steps, in the card's order. */
    val steps: List<PhoneStep> get() = PhoneStep.entries.filter { it in wanted }

    val complete: Boolean get() = done.containsAll(wanted)

    companion object {
        /**
         * What a file too old to say what its phone had is taken to want: reminders and the weekly copy, which a
         * tutor who backs up keeps. The calendar is not assumed — a mirror nobody asked for is clutter in theirs.
         */
        val UNKNOWN: Set<PhoneStep> = setOf(PhoneStep.REMINDERS, PhoneStep.WEEKLY_COPY)

        /**
         * @param reminded whether lesson reminders are on and the phone lets them through.
         * @param mirrored whether lessons are mirrored to a calendar on this phone.
         * @param copying whether the weekly copy has a folder on this phone.
         */
        fun of(wanted: Set<PhoneStep>, reminded: Boolean, mirrored: Boolean, copying: Boolean): PhoneSetup =
            PhoneSetup(
                wanted = wanted,
                done = buildSet {
                    if (reminded) add(PhoneStep.REMINDERS)
                    if (mirrored) add(PhoneStep.CALENDAR)
                    if (copying) add(PhoneStep.WEEKLY_COPY)
                }.intersect(wanted),
            )
    }
}
