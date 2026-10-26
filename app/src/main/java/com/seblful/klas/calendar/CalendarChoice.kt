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

/** Managed addresses before a change, the ones to retire, and the invitation still to add. */
internal data class GuestPlan(val managed: Set<String>, val remove: Set<String>, val add: String?)

/** A null [managed] is a legacy event: adopt its matching student address once. */
internal fun guestPlan(current: Set<String>, managed: Set<String>?, guest: Guest?): GuestPlan {
    val email = guest?.email?.takeIf { it.isNotBlank() }
    val owned = managed ?: current.intersect(setOfNotNull(email))
    val desired = email?.takeIf { guest?.invited == true }
    return GuestPlan(
        managed = owned,
        remove = owned - setOfNotNull(desired),
        add = desired?.takeIf { it !in current },
    )
}
