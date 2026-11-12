package com.seblful.klas.settings

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsChange
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.data.settings.WritesWithoutEffects
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.maintenance.Maintenance
import com.seblful.klas.notifications.Notifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The settings writer: stores a change and finishes what it owes — calendar, alarms, digests, channels and
 * the auto-completion sweep — before returning, whichever fields it moved.
 */
@SingleIn(AppScope::class)
class SettingsWorkflow @Inject constructor(
    private val settings: SettingsRepository,
    private val calendar: CalendarMirror,
    private val reminders: ReminderScheduler,
    private val maintenance: Maintenance,
    private val notifier: Notifier,
) {
    /** Returns how many lessons the auto-completion sweep settled; zero when the change owed none. */
    @OptIn(WritesWithoutEffects::class)
    suspend fun update(transform: (AppSettings) -> AppSettings): Int {
        val change = settings.update(transform)
        when (change.calendarWork) {
            SettingsChange.CalendarWork.RESYNC -> calendar.resyncAll()
            SettingsChange.CalendarWork.RESYNC_UPCOMING -> calendar.resyncUpcoming()
            SettingsChange.CalendarWork.WITHDRAW -> calendar.clear()
            SettingsChange.CalendarWork.NONE -> Unit
        }
        if (change.replansReminders || change.replansDigests) reminders.refreshNow()
        if (change.movesChannels) notifier.ensureChannels()
        return if (change.sweepsDueLessons) maintenance.run().lessons else 0
    }

    suspend fun dueForCompletion(): Int = maintenance.dueForCompletion()
}
