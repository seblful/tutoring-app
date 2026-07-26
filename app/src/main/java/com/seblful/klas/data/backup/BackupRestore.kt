package com.seblful.klas.data.backup

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything restoring a backup entails beyond replacing the rows: re-planning reminders,
 * re-mirroring the classes to *this* device's calendar (the imported event ids came from another
 * one), and marking first-run setup done — restored data *is* the setup, so a tutor who restores
 * on a fresh install lands in the app instead of being asked to add a subject and a student.
 *
 * Both entry points (Ещё → Резервная копия and the first-run screen) go through here, so neither
 * can forget a step. The file reading itself stays in the view models, where the SAF `Uri` lives.
 */
@Singleton
class BackupRestore @Inject constructor(
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val classes: ClassRepository,
    private val reminders: ReminderScheduler,
) {
    /**
     * Replaces all data with [json]. Throws if it isn't a supported backup — the import is one
     * transaction, so a rejected file leaves the current data (and the onboarding flag) untouched.
     */
    suspend fun restore(json: String) {
        backup.importJson(json)
        settings.update { it.copy(onboardingComplete = true) }
        // Imported classes change what's upcoming/overdue — re-run auto-complete & reminders.
        reminders.refreshNow()
        runCatching { classes.resyncCalendar() } // best-effort, like all calendar work
    }
}
