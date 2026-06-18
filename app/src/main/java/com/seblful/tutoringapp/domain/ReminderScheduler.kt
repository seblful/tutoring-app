package com.seblful.tutoringapp.domain

/**
 * Outbound port: "the set of classes changed, re-derive the time-driven side effects now"
 * (auto-completion + reminder notifications). Lets the data layer trigger rescheduling without
 * depending on WorkManager. Implemented in the `work` package.
 */
fun interface ReminderScheduler {
    fun refreshNow()
}
