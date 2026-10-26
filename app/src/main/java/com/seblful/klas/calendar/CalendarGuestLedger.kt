package com.seblful.klas.calendar

import android.content.Context

/** Device-only invitation ownership, including withdrawals owed before any event content may change. */
internal class CalendarGuestLedger(context: Context) {
    private val prefs = context.getSharedPreferences("calendar-invitations", Context.MODE_PRIVATE)

    /** Null marks an event made before ownership was tracked; an empty set is already tracked. */
    fun managed(eventId: Long): Set<String>? = prefs.getStringSet("managed-$eventId", null)?.toSet()

    fun retiring(eventId: Long): Set<String> = prefs.getStringSet("retiring-$eventId", emptySet()).orEmpty().toSet()

    /** Keep a legacy pre-edit address even when permissions or the provider prevent its withdrawal now. */
    fun rememberPrevious(eventId: Long, previousEmail: String?) {
        val email = previousEmail?.takeIf { it.isNotBlank() } ?: return
        val managed = managed(eventId) ?: setOf(email)
        if (email in managed) write(eventId, managed, retiring(eventId) + email)
    }

    fun write(eventId: Long, managed: Set<String>, retiring: Set<String> = emptySet()) {
        if (this.managed(eventId) == managed && this.retiring(eventId) == retiring) return
        // Called on IO. The ownership claim must survive a process stop immediately after provider insertion.
        check(
            prefs.edit()
                .putStringSet("managed-$eventId", managed)
                .putStringSet("retiring-$eventId", retiring)
                .commit(),
        ) { "Cannot keep calendar invitation ownership" }
    }

    fun forget(eventId: Long) {
        check(prefs.edit().remove("managed-$eventId").remove("retiring-$eventId").commit()) {
            "Cannot clear calendar invitation ownership"
        }
    }
}
