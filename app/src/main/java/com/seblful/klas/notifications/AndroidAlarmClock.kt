package com.seblful.klas.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [AlarmClock] over `AlarmManager`. The bytes, as `FileCrashLogStore` is to `CrashLogger`.
 *
 * `RTC_WAKEUP` because a reminder is about a wall-clock time and has to wake the device, and
 * `setAndAllowWhileIdle` on both paths because Doze otherwise defers a plain `set` indefinitely — it permits
 * one such wake-up per app per maintenance window, which is exactly the budget one reminder needs.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidAlarmClock @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlarmClock {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun setExact(classId: Long, atMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, arming(classId))
    }

    override fun setInexact(classId: Long, atMillis: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, arming(classId))
    }

    /**
     * `FLAG_NO_CREATE`, so cancelling an alarm nothing armed does nothing.
     *
     * `FLAG_UPDATE_CURRENT` would *create* the `PendingIntent` it is about to cancel — a stray allocation,
     * and a live intent for a lesson that never had one. Every re-arm cancels the whole previously armed
     * set, so this runs often.
     */
    override fun cancel(classId: Long) {
        val armed = PendingIntent.getBroadcast(
            context,
            classId.toInt(),
            remindIntent(classId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        armed?.let { alarmManager.cancel(it) }
    }

    private fun arming(classId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        classId.toInt(),
        remindIntent(classId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun remindIntent(classId: Long): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
}

/** [ArmedReminders] in `SharedPreferences` — the one part of `ReminderAlarms`' bookkeeping that needs a device. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PrefsArmedReminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ArmedReminders {

    /**
     * Ids are stored as strings because `SharedPreferences` has a `StringSet` and no `LongSet`. An entry that
     * does not parse is dropped rather than throwing: the worst it can cost is one stale alarm nobody cancels,
     * and this is read on the path that arms every reminder the app has.
     */
    override fun read(): Set<Long> =
        prefs().getStringSet(KEY_ARMED, emptySet())
            .orEmpty()
            .mapNotNullTo(mutableSetOf()) { it.toLongOrNull() }

    override fun write(ids: Set<Long>) {
        prefs().edit { putStringSet(KEY_ARMED, ids.mapTo(mutableSetOf()) { it.toString() }) }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "reminder-alarms"
        const val KEY_ARMED = "armed-class-ids"
    }
}
