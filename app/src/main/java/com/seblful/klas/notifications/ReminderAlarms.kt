package com.seblful.klas.notifications

import com.seblful.klas.domain.reminders.Reminders
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Arms the app's lesson reminders as **alarms**, one per planned lesson.
 *
 * **Why not WorkManager.** Reminders used to be `OneTimeWorkRequest`s with an initial delay, and that
 * delay is explicitly a *lower bound*: WorkManager batches, defers under Doze and App Standby, and is
 * allowed to run work much later than asked. For most background work that is the right trade. A
 * reminder is the opposite kind of thing — "your lesson starts in an hour" is worthless an hour late,
 * and `Reminders.shouldPost` will still post it right up to the lesson's *end*, so a late firing
 * arrived looking exactly like a punctual one. The platform primitive for a user-visible, time-critical
 * notification is an alarm, and `ReminderScheduler` was already the seam that makes this an adapter
 * swap rather than a redesign.
 *
 * **Exact where allowed, inexact where not.** `SCHEDULE_EXACT_ALARM` is granted by default on Android
 * 13 but revocable, and absent on 14+ unless the user grants it, so [ExactAlarmPermission] is asked
 * every time rather than assumed. Without it the alarm is still set, `setAndAllowWhileIdle` — which is
 * late but far closer than batched work, and Doze permits one such wake-up per app per window. The app
 * never prompts for the exact-alarm permission: a tutoring reminder does not justify the dialog, and the
 * inexact path is a genuine fallback rather than a broken one.
 *
 * **Whether an alarm can be exact has one definition, and it is the port's.** This class used to answer
 * it with a private `canBeExact()`, which is the same mistake `AndroidNotifier` made about the
 * notification permission: the answer decided how the app behaved and there was no way for anything else
 * to read it, so the settings screen could not say that reminders had silently become approximate. Now
 * the arming and the reporting ask one object — see [com.seblful.klas.ui.settings.ReminderDelivery].
 *
 * **Why the ids are remembered.** The plan is re-derived from scratch on every change, so applying it
 * means replacing future alarms while preserving those already due. Cancellation uses each class id.
 * The adapter cancels an alarm by rebuilding the `PendingIntent` that set it, which needs the class
 * id, so the ids currently armed are kept by [ArmedReminders]. They survive the process, which is the
 * point: an alarm set yesterday must be cancellable after a restart.
 *
 * **What is left in this class is exactly that discipline.** The `PendingIntent` building and the
 * preferences read went behind [AlarmClock] and [ArmedReminders], which is the split `CrashLogger` and
 * `CrashLogStore` already draw: everything worth getting wrong is here, and the platform calls are on the
 * other side. Before that, "re-arming replaces exactly the previous set" and "an alarm set yesterday is
 * cancellable after a restart" were the two things this class exists to guarantee and the two things only a
 * device could check — the class had no test of any kind.
 *
 * Alarms do **not** survive a reboot — [BootReceiver] re-derives the plan instead.
 */
@SingleIn(AppScope::class)
class ReminderAlarms @Inject constructor(
    private val alarms: AlarmClock,
    private val armed: ArmedReminders,
    private val exactAlarms: ExactAlarmPermission,
) {
    /**
     * Arms the future entries in [plans], preserving existing alarms for due entries.
     *
     * Future entries are cancelled then reset rather than diffed: the plan is authoritative and small
     * (one lesson a reminder, inside a week's horizon), and a diff would have to account for why plans
     * differ — a lesson whose time moved by a minute is a different alarm at the same id.
     *
     * Among ids not already due, the **order** matters: cancel the previous alarms, arm the future
     * entries, then replace the record. Writing the record before arming could lose old ids on a
     * failure partway and leave alarms nobody can reach; arming before cancelling would cancel
     * an id the new plan had just re-armed.
     *
     * **One exception to the cancel, and it is the one alarm that has to survive.** A plan entry that is
     * already [Reminders.Plan.due] cannot be armed — its instant has passed, and setting it would fire on
     * the spot — but it is still *owed*: an inexact alarm (the fallback where the exact-alarm grant is
     * missing) may not have fired yet, and Doze is free to hold it past the moment it was asked for.
     * Cancelling it because the plan can no longer re-state it is how a promised reminder silently never
     * arrives, so a due entry is left exactly where it is and kept in the record. That costs nothing
     * else: the entry stays in the plan only while its lesson is still to come or running, so its alarm
     * is cancelled and forgotten by the first pass after the lesson ends ([Reminders.plan]).
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
