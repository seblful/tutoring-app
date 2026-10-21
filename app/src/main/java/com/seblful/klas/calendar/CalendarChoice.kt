package com.seblful.klas.calendar

/**
 * One of the phone's calendars, as far as choosing one to mirror into is concerned.
 *
 * @param accessLevel the platform's own `CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL`, kept as the
 * raw int rather than mapped onto an enum: the values are ordered and the rule below compares them,
 * which is exactly what the platform intends them for.
 */
data class CalendarRow(
    val id: Long,
    val isPrimary: Boolean,
    val visible: Boolean,
    val accessLevel: Int,
)

/** Which calendar a mirrored lesson is written to — the rule, with no `ContentResolver` in sight. */
object CalendarChoice {

    /**
     * The lowest access level that may insert events — `CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR`.
     *
     * The platform's constant, restated here so the rule that uses it is reachable without Android. It is
     * public API and has held this value since the provider existed; the reason it is spelled out is the
     * bug above, which was a matter of picking the *wrong* level rather than of the number changing.
     */
    const val MIN_ACCESS_LEVEL = 500

    /** The calendar to write to, or null when none of [rows] may be written to at all. */
    fun preferred(rows: List<CalendarRow>): Long? = rows
        .filter { it.accessLevel >= MIN_ACCESS_LEVEL }
        .minWithOrNull(
            compareBy<CalendarRow> { !it.isPrimary }
                .thenBy { !it.visible }
                .thenByDescending { it.accessLevel },
        )
        ?.id
}

/**
 * What an event's attendee list owes the student — see [guestAction].
 *
 * Three answers rather than a boolean, because "leave it alone" is a real outcome with two quite different
 * causes: the student has no address to invite, or they are already invited and nothing has changed.
 */
sealed interface GuestAction {

    /** Nothing to do. */
    data object None : GuestAction

    /** Add [email] as an attendee, so the calendar account emails them about the lesson. */
    data class Add(val email: String) : GuestAction

    /** Drop [email], withdrawing an invitation the app sent earlier. */
    data class Remove(val email: String) : GuestAction
}

/** What this event's attendees owe [guest], given the addresses [current] already on it. */
fun guestAction(current: List<String>, guest: Guest?): GuestAction {
    val email = guest?.email?.takeIf { it.isNotBlank() } ?: return GuestAction.None
    val present = email in current
    return when {
        guest.invited && !present -> GuestAction.Add(email)
        !guest.invited && present -> GuestAction.Remove(email)
        else -> GuestAction.None
    }
}
