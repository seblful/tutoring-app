package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.SettingsDao
import com.seblful.repetit.data.local.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App settings. A missing row maps to defaults. */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val emailInvitesEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val reminderLeadMinutes: Int = 60,
    val autoCompleteEnabled: Boolean = false,
    val themeMode: String = "SYSTEM",
    val defaultLessonMinutes: Int = 60,
    /** Pre-fills a new group's hourly rate, in minor units; 0 means "not set". */
    val defaultGroupPriceMinor: Long = 0,
    val currency: String = "BYN",
    val showGroupsTab: Boolean = true,
)

/**
 * The single settings row.
 *
 * Writes go through one [update] rather than a setter per field. Eleven settings used to mean
 * eleven one-line setters here and eleven more wrappers in the view model, so adding a setting
 * touched six files to say the same thing six times. A transform says it once, and matches how the
 * app's other forms are already edited (`ClassEditViewModel.update`, `GroupFormViewModel.update`).
 *
 * Settings are **device-local** — deliberately not part of the backup snapshot.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingsDao,
) {
    fun observe(): Flow<AppSettings> = dao.observe().map { it.toSettings() }

    suspend fun get(): AppSettings = dao.get().toSettings()

    /**
     * Applies [transform] to the current settings and stores the result. Read-modify-write against
     * the single row, so a caller changes one field with `update { it.copy(currency = "RUB") }`
     * and leaves the rest alone.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dao.upsert(transform(get()).toEntity())
    }

    private fun SettingsEntity?.toSettings(): AppSettings =
        if (this == null) AppSettings()
        else AppSettings(
            notificationsEnabled = notificationsEnabled,
            calendarSyncEnabled = calendarSyncEnabled,
            emailInvitesEnabled = emailInvitesEnabled,
            onboardingComplete = onboardingComplete,
            reminderLeadMinutes = reminderLeadMinutes,
            autoCompleteEnabled = autoCompleteEnabled,
            themeMode = themeMode,
            defaultLessonMinutes = defaultLessonMinutes,
            defaultGroupPriceMinor = defaultGroupPriceMinor,
            currency = currency,
            showGroupsTab = showGroupsTab,
        )

    /**
     * The row is always written whole from Kotlin, so this — not the entity's `defaultValue`
     * annotations — decides what a fresh install stores. Those annotations govern the migration
     * backfill only (see `SettingsEntity.emailInvitesEnabled`, where the two deliberately differ).
     */
    private fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
        notificationsEnabled = notificationsEnabled,
        calendarSyncEnabled = calendarSyncEnabled,
        emailInvitesEnabled = emailInvitesEnabled,
        onboardingComplete = onboardingComplete,
        reminderLeadMinutes = reminderLeadMinutes,
        autoCompleteEnabled = autoCompleteEnabled,
        themeMode = themeMode,
        defaultLessonMinutes = defaultLessonMinutes,
        defaultGroupPriceMinor = defaultGroupPriceMinor,
        currency = currency,
        showGroupsTab = showGroupsTab,
    )
}
