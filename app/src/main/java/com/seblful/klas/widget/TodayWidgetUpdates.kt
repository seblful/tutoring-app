package com.seblful.klas.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps placed Today widgets current. A widget draws only when told to, and its own session ends soon after
 * a render, so a lesson booked, moved or taught in the app — or a new day — reaches it only through here.
 */
@SingleIn(AppScope::class)
@Inject
class TodayWidgetUpdates(
    @param:ApplicationContext private val context: Context,
    private val clock: WallClock,
    val lessons: TodayLessons,
) {
    /**
     * Re-renders every Today widget whenever today's lessons change, for as long as the process lives.
     * The first emission renders too, so a process started for any reason repairs a widget left stale.
     */
    suspend fun keepCurrent() {
        lessons.observe().collect { refresh() }
    }

    /** No widget placed makes this a no-op. A failed render is dropped: the next change retries it. */
    suspend fun refresh() {
        runCatchingCancellable { TodayWidget().updateAll(context) }
    }

    /**
     * Wakes [TodayWidgetReceiver] at the next local midnight, without waking the device: a widget is only
     * seen on a screen that is on, which delivers the alarm. Replaces the one armed before.
     */
    fun armMidnight() {
        val today = clock.now().toLocalDateTime(clock.zone).date
        val midnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(clock.zone)
        alarms.set(AlarmManager.RTC, midnight.toEpochMilliseconds(), newDay())
    }

    fun disarmMidnight() {
        alarms.cancel(newDay())
    }

    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun newDay(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TodayWidgetReceiver::class.java).setAction(ACTION_NEW_DAY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_NEW_DAY = "com.seblful.klas.action.WIDGET_NEW_DAY"
    }
}
