package com.seblful.klas.notifications

import com.seblful.klas.domain.reminders.Digests

/**
 * What a tray entry shows, decided where a JVM test can reach it.
 *
 * [Notifier]'s own contract says that "which lessons a digest names, whose names those are and whether it
 * is worth posting at all are decisions, and decisions live where a JVM test can reach them" — and then
 * left out the last mile. How many of those lessons actually fit in the notification, and what it says
 * about the ones it could not name, were arithmetic inside `AndroidNotifier`: 355 lines behind a `Context`,
 * with no test in either source set.
 *
 * This is that last mile. The adapter keeps `NotificationCompat`, the channels, the pending intents and
 * the phone's clock face — things that genuinely need Android — and asks here for what to put in them.
 */
object TrayContent {

    /**
     * How many lessons a digest that *lists* lessons names before it starts counting the rest — the
     * morning agenda and the evening unfinished list both.
     *
     * `InboxStyle` draws each line whole and elides the ones that do not fit rather than truncating the
     * middle of a sentence, and it shows about six. So a fuller day says how many more it is not naming
     * instead of stopping mid-morning.
     */
    const val MAX_AGENDA_LINES = 6

    /**
     * The agenda as it will be posted: the lessons that fit, and how many were left out.
     *
     * @param lines the entries to draw, already capped at [MAX_AGENDA_LINES] and in the order [Digests]
     *   assembled them.
     * @param more how many lessons the day holds beyond [lines] — **0 when none were dropped**, which is
     *   what a caller checks rather than re-deriving the subtraction. Never negative.
     */
    data class AgendaPosting(val lines: List<Digests.Agenda.Entry>, val more: Int)

    /** What [agenda] comes to on screen — see [AgendaPosting]. */
    fun agenda(agenda: Digests.Agenda): AgendaPosting = capped(agenda.lessons)

    /**
     * What [unfinished] comes to on screen — the same cap and the same «ещё N», because it is the same
     * `InboxStyle` list drawn by the same platform at the other end of the day.
     *
     * Stated as its own function rather than by the caller reaching for [agenda] with a hand-built
     * `Agenda`: the two digests are different things that happen to be listed alike, and coupling them
     * through a type neither of them is would make the evening list carry a `DaySummary` it has no use
     * for. The shared part is the cap ([capped]).
     */
    fun unfinished(unfinished: Digests.Unfinished): AgendaPosting = capped(unfinished.lessons)

    private fun capped(lessons: List<Digests.Agenda.Entry>): AgendaPosting = AgendaPosting(
        lines = lessons.take(MAX_AGENDA_LINES),
        more = (lessons.size - MAX_AGENDA_LINES).coerceAtLeast(0),
    )
}
