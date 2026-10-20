package com.seblful.klas.settings

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsChange
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Stores a preference and finishes its calendar and maintenance work before returning. */
@SingleIn(AppScope::class)
class SettingsWorkflow @Inject constructor(
    private val settings: SettingsRepository,
    private val calendar: CalendarMirror,
    private val reminders: ReminderScheduler,
    private val maintenance: Maintenance,
) {
    suspend fun update(transform: (AppSettings) -> AppSettings): Int {
        val change = settings.update(transform)
        when (change.calendarWork) {
            SettingsChange.CalendarWork.RESYNC -> calendar.resyncAll()
            SettingsChange.CalendarWork.WITHDRAW -> calendar.clear()
            SettingsChange.CalendarWork.NONE -> Unit
        }
        if (change.replansReminders || change.replansDigests) reminders.refreshNow()
        return if (change.sweepsDueLessons) maintenance.run().lessons else 0
    }

    suspend fun dueForCompletion(): Int = maintenance.dueForCompletion()
}
