package com.seblful.klas.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.seblful.klas.domain.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
 * 13 but revocable, and absent on 14+ unless the user grants it, so [canBeExact] is asked every time
 * rather than assumed. Without it the alarm is still set, `setAndAllowWhileIdle` — which is late but
 * far closer than batched work, and Doze permits one such wake-up per app per window. The app never
 * prompts for the exact-alarm permission: a tutoring reminder does not justify the dialog, and the
 * inexact path is a genuine fallback rather than a broken one.
 *
 * **Why the ids are remembered.** The plan is re-derived from scratch on every change, so arming it
 * means disarming whatever was armed before — and `AlarmManager` has no "cancel everything of mine".
 * An alarm can only be cancelled by rebuilding the `PendingIntent` that set it, which needs the class
 * id, so the ids currently armed are kept in preferences. They survive the process, which is the point:
 * an alarm set yesterday must be cancellable after a restart.
 *
 * Alarms do **not** survive a reboot — [BootReceiver] re-derives the plan instead.
 */
@Singleton
class ReminderAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    private val armedIds: Set<Long>
        get() = prefs().getStringSet(KEY_ARMED, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    /**
     * Replaces every armed reminder with exactly [plans].
     *
     * Cancel-then-set rather than a diff: the plan is authoritative and small (one lesson a reminder,
     * inside a week's horizon), and a diff would have to be right about *why* two plans differ — a
     * lesson whose time moved by a minute is a different alarm at the same id.
     */
    fun set(plans: List<Reminders.Plan>) {
        armedIds.forEach { cancel(it) }
        val now = System.currentTimeMillis()
        plans.forEach { plan -> arm(plan.classId, at = now + plan.delayMillis) }
        prefs().edit { putStringSet(KEY_ARMED, plans.map { it.classId.toString() }.toSet()) }
    }

    private fun arm(classId: Long, at: Long) {
        val intent = pendingIntent(classId)
        if (canBeExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun cancel(classId: Long) {
        alarmManager.cancel(pendingIntent(classId))
    }

    /**
     * `RTC_WAKEUP` because a reminder is about a wall-clock time and has to wake the device; the
     * request code is the class id, which is what makes one lesson's alarm addressable on its own.
     */
    private fun pendingIntent(classId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_CLASS_ID, classId)
        return PendingIntent.getBroadcast(
            context,
            classId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Asked each time: the permission is revocable, and absent by default on Android 14+. */
    private fun canBeExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "reminder-alarms"
        const val KEY_ARMED = "armed-class-ids"
    }
}
