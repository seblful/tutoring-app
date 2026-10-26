package com.seblful.klas.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.seblful.klas.ACTION_PREFIX
import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Keeps placed widgets current. A widget draws only when told to, and its own session ends soon after a
 * render, so a lesson booked, moved or taught in the app, a new language, a new day or a new clock reaches
 * it only through here.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetUpdates(
    @param:ApplicationContext private val context: Context,
    private val clock: WallClock,
    val lessons: TodayLessons,
    val displayLocale: DisplayLocale,
    private val previews: WidgetPreviews,
    private val settings: SettingsRepository,
) {
    /** The lock-screen choices, now and on each change — whether Today lists lessons on a keyguard. */
    fun privacy(): Flow<Privacy> = settings.observe().map { it.privacy }.distinctUntilChanged()

    /**
     * Re-renders the widgets whenever what they show changes, for as long as the process lives. The first
     * emissions render too, so a process started for any reason repairs a widget left stale.
     */
    suspend fun keepCurrent(): Unit = coroutineScope {
        launch { lessons.observe().collect { update { TodayWidget().updateAll(context) } } }
        launch { privacy().collect { update { TodayWidget().updateAll(context) } } }
        launch {
            displayLocale.observe().collect { locale ->
                refresh()
                update { previews.publish(locale) }
            }
        }
    }

    /**
     * Re-renders every widget, and re-arms Today's next redraw in the zone the clock is now in. No widget placed
     * makes this a no-op.
     */
    suspend fun refresh() {
        update {
            if (GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isNotEmpty()) {
                armNextChange(lessons.observe().first())
            }
            TodayWidget().updateAll(context)
        }
        update { QuickActionsWidget().updateAll(context) }
    }

    /** A failed render is dropped: the next change retries it. */
    private suspend fun update(render: suspend () -> Unit) {
        runCatchingCancellable { render() }
    }

    /** The moment a render draws «On now» against. */
    fun now(): Instant = clock.now()

    /**
     * Wakes [TodayWidgetReceiver] when [today]'s next lesson starts or ends — «On now» moves — or else at the
     * next local midnight, without waking the device: a widget is only seen on a screen that is on, which
     * delivers the alarm. Replaces the one armed before.
     */
    fun armNextChange(today: List<TodayLesson>) {
        val now = clock.now()
        val midnight = now.toLocalDateTime(clock.zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(clock.zone)
        val at = TodayLessons.nextChange(today, now)?.takeIf { it < midnight } ?: midnight
        alarms.set(AlarmManager.RTC, at.toEpochMilliseconds(), newDay())
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
        const val ACTION_NEW_DAY = "${ACTION_PREFIX}WIDGET_NEW_DAY"
    }
}
