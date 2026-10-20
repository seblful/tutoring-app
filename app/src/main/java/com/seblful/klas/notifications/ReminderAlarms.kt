package com.seblful.klas.notifications

import com.seblful.klas.domain.reminders.Reminders
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Arms one alarm per lesson reminder. Exact timing is used when permitted; otherwise reminders use
 * inexact alarms. [ArmedReminders] persists IDs so alarms can be replaced after process restarts;
 * [BootReceiver] rebuilds the plan after a reboot.
 */
@SingleIn(AppScope::class)
class ReminderAlarms @Inject constructor(
    private val alarms: AlarmClock,
    private val armed: ArmedReminders,
    private val exactAlarms: ExactAlarmPermission,
) {
    /**
     * Applies [plans], replacing future alarms while preserving alarms already due. It cancels the old
     * alarms, sets the new ones, then saves their IDs. Due entries remain armed because an inexact alarm
     * may still be waiting to fire; they are forgotten when the lesson ends.
     */
    fun set(plans: List<Reminders.Plan>) {
        val owed = plans.filter { it.due }.mapTo(mutableSetOf()) { it.classId }
        armed.read().filterNot { it in owed }.forEach { alarms.cancel(it) }
        // The plan carries the firing instant itself — nothing here reads a clock.
        plans.forEach { plan ->
            // Owed entries retain any alarm already set for them. Re-arming one whose instant has
            // passed could deliver it on the spot or post it twice, so a due entry never arms
            // a new alarm.
            if (plan.due) return@forEach
            val at = plan.fireAt.toEpochMilliseconds()
            // The one decision here, and it is read from the port rather than answered privately.
            if (exactAlarms.isGranted()) alarms.setExact(plan.classId, at) else alarms.setInexact(plan.classId, at)
        }
        armed.write(plans.mapTo(mutableSetOf()) { it.classId })
    }
}
