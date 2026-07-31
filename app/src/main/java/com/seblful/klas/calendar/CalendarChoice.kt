package com.seblful.klas.calendar

/**
 * One of the phone's calendars, as far as choosing one to mirror into is concerned.
 *
 * @param accessLevel the platform's own `CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL`, kept as the
 *   raw int rather than mapped onto an enum: the values are ordered and the rule below compares them,
 *   which is exactly what the platform intends them for.
 */
data class CalendarRow(
    val id: Long,
    val isPrimary: Boolean,
    val visible: Boolean,
    val accessLevel: Int,
)

/**
 * Which calendar a mirrored lesson is written to — the rule, with no `ContentResolver` in sight.
 *
 * This was a `WHERE` clause and a three-key `ORDER BY` inside [DeviceCalendarSync], a 200-line adapter
 * with no test in either source set, and it is not a detail: **the threshold has already shipped wrong
 * once.** Demanding `CAL_ACCESS_OWNER` matched nothing on devices that report a synced Google account
 * calendar as EDITOR or CONTRIBUTOR, so on those phones every event the app thought it mirrored silently
 * no-opped — the failure mode a best-effort port is most likely to hide.
 */
object CalendarChoice {

    /**
     * The lowest access level that may insert events — `CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR`.
     *
     * The platform's constant, restated here so the rule that uses it is reachable without Android. It is
     * public API and has held this value since the provider existed; the reason it is spelled out is the
     * bug above, which was a matter of picking the *wrong* level rather than of the number changing.
     */
    const val MIN_ACCESS_LEVEL = 500

    /**
     * The calendar to write to, or null when none of [rows] may be written to at all.
     *
     * Anything at or above [MIN_ACCESS_LEVEL] is eligible; among those the order of preference is **the
     * account's primary, then a visible one, then a more privileged one**. Ties fall to whichever the
     * platform listed first, which is the same answer the `ORDER BY` this replaces gave.
     */
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

/**
 * What this event's attendees owe [guest], given the addresses [current] already on it.
 *
 * The whole of "invite the student, or take the invitation back" as one value. Two properties matter and
 * both used to be spread across three private adapter methods:
 *
 * - **It touches exactly one address.** Guests the tutor added themselves in their own calendar are never
 *   named by any answer here, so re-syncing cannot quietly clear them.
 * - **It is idempotent in both directions.** Adding somebody already there and removing somebody already
 *   gone are both [GuestAction.None], so a re-sync after the tutor flips the invite setting does the one
 *   thing that changed and nothing else.
 *
 * "Don't invite" is not the same as "no address" — see [Guest]. A guest with an address and
 * [Guest.invited] false is what *withdraws* an invitation already sent; a null guest, or one with a blank
 * address, leaves attendees untouched.
 *
 * Addresses are compared exactly, as the provider query this replaces did. An account that returns the
 * same mailbox in different case would be treated as a different attendee, which is the behaviour that
 * shipped and is left alone here rather than changed in passing.
 */
fun guestAction(current: List<String>, guest: Guest?): GuestAction {
    val email = guest?.email?.takeIf { it.isNotBlank() } ?: return GuestAction.None
    val present = email in current
    return when {
        guest.invited && !present -> GuestAction.Add(email)
        !guest.invited && present -> GuestAction.Remove(email)
        else -> GuestAction.None
    }
}
