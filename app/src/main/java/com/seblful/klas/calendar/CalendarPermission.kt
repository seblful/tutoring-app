package com.seblful.klas.calendar

/** hether the app currently holds the calendar permissions the mirror needs. */
interface CalendarPermission {

    /** rue when both read and write calendar access have been granted. */
    fun isGranted(): Boolean
}
