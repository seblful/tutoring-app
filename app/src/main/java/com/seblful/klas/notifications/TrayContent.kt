package com.seblful.klas.notifications

import com.seblful.klas.domain.Digests

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
     * How many lessons the morning agenda names before it starts counting the rest.
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
    fun agenda(agenda: Digests.Agenda): AgendaPosting = AgendaPosting(
        lines = agenda.lessons.take(MAX_AGENDA_LINES),
        more = (agenda.lessons.size - MAX_AGENDA_LINES).coerceAtLeast(0),
    )
}
