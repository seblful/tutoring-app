package com.seblful.klas.calendar

/** Whether the app currently holds the calendar permissions the mirror needs. */
interface CalendarPermission {

    /** True when both read and write calendar access have been granted. */
    fun isGranted(): Boolean
}
