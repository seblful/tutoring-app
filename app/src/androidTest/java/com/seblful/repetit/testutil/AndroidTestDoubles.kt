package com.seblful.repetit.testutil

import com.seblful.repetit.domain.ReminderScheduler
import com.seblful.repetit.domain.Reminders

/**
 * A [ReminderScheduler] that only records. Instrumented tests build repositories by hand and each
 * needs *some* scheduler; before this they each wrote their own no-op lambda, which stopped
 * compiling the moment the port gained a second method. One double, four call sites.
 *
 * Deliberately a near-copy of the unit-test double of the same name: `src/test` and `src/androidTest`
 * are separate source sets with no shared fixtures module, so neither can see the other's.
 */
class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    val schedules = mutableListOf<List<Reminders.Plan>>()

    override fun refreshNow() {
        refreshCount++
    }

    override fun schedule(plans: List<Reminders.Plan>) {
        schedules += plans
    }
}
