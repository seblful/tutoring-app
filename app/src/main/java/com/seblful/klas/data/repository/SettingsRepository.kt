package com.seblful.klas.data.repository

import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     *
     * **Serialized, because the row is written whole.** The read and the write are two suspension
     * points around one row, and the write carries every field — so two updates that overlap lose
     * one: both read the same settings, both write their own copy of them, and the second copy has
     * the first one's change reverted in it. It is not a hypothetical interleaving. The settings
     * screen launches one coroutine per switch, so flipping two in quick succession is enough; and
     * [SettingsViewModel.setCalendarSyncEnabled] holds its update open across a whole calendar
     * resync, which is a long time for another toggle to land in.
     *
     * The lock is the repository's own, and this is the only writer of the row, so it is the whole
     * story rather than half of one. A `Mutex` rather than a Room `@Transaction`: what needs to be
     * atomic is `transform` — Kotlin the caller passed in — and not merely the two queries.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        writeLock.withLock { dao.upsert(transform(get()).toEntity()) }
    }

    private val writeLock = Mutex()

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
