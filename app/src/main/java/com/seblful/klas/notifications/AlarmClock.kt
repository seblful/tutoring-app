package com.seblful.klas.notifications

/**
 * Setting and cancelling one lesson's alarm — the platform half of [ReminderAlarms], and nothing else.
 *
 * A port for the reason `CrashLogStore` is one: everything worth getting wrong about reminders is the
 * *replacement discipline* — arming a new plan means disarming exactly what was armed before, and
 * `AlarmManager` has no "cancel everything of mine" — so that discipline sits above this line, where a JVM
 * test drives it, and the `PendingIntent` building stays here.
 *
 * **Exact versus inexact is not this port's choice.** Which one to use is [ExactAlarmPermission]'s answer,
 * read by [ReminderAlarms]; a port that decided it would be a second definition of the same question.
 *
 * Alarms are addressed by **class id**, which is the whole reason one lesson's can be taken back on its own:
 * the id is the `PendingIntent`'s request code, and rebuilding that intent is the only way to cancel it.
 */
interface AlarmClock {

    /** Wakes the device at [atMillis] for [classId], as close to exactly as the platform allows. */
    fun setExact(classId: Long, atMillis: Long)

    /**
     * As [setExact], but through the inexact path Doze may hold until its next maintenance window —
     * the fallback where `SCHEDULE_EXACT_ALARM` is not granted. Late, but far closer than batched work.
     */
    fun setInexact(classId: Long, atMillis: Long)

    /** Takes back [classId]'s alarm. A no-op when nothing is armed for it. */
    fun cancel(classId: Long)
}

/**
 * The class ids currently armed, across process death.
 *
 * A port with two methods, for one reason: `AlarmManager` cannot be asked what it holds, so the app has to
 * remember — and "remember" is a `SharedPreferences` read, which is the one line of [ReminderAlarms] that
 * could not run on the JVM. The set has to **survive the process**, because an alarm set yesterday must be
 * cancellable after a restart; that is what makes this storage rather than a field.
 */
interface ArmedReminders {

    /** The ids armed as far as the app knows. Empty before anything has ever been armed. */
    fun read(): Set<Long>

    /** Records [ids] as the ids now armed, replacing whatever was recorded before. */
    fun write(ids: Set<Long>)
}
