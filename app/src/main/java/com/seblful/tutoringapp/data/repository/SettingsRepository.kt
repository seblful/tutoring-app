package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.SettingsDao
import com.seblful.tutoringapp.data.local.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App settings. A missing row maps to defaults. */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val reminderLeadMinutes: Int = 60,
    val autoCompleteEnabled: Boolean = true,
    val themeMode: String = "SYSTEM",
    val defaultLessonMinutes: Int = 60,
    val currency: String = "BYN",
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingsDao,
) {
    fun observe(): Flow<AppSettings> = dao.observe().map { it.toSettings() }

    suspend fun get(): AppSettings = dao.get().toSettings()

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        update { it.copy(notificationsEnabled = enabled) }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) =
        update { it.copy(calendarSyncEnabled = enabled) }

    suspend fun setOnboardingComplete(complete: Boolean) =
        update { it.copy(onboardingComplete = complete) }

    suspend fun setReminderLeadMinutes(minutes: Int) =
        update { it.copy(reminderLeadMinutes = minutes) }

    suspend fun setAutoCompleteEnabled(enabled: Boolean) =
        update { it.copy(autoCompleteEnabled = enabled) }

    suspend fun setThemeMode(mode: String) =
        update { it.copy(themeMode = mode) }

    suspend fun setDefaultLessonMinutes(minutes: Int) =
        update { it.copy(defaultLessonMinutes = minutes) }

    suspend fun setCurrency(currency: String) =
        update { it.copy(currency = currency) }

    private suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
        val current = dao.get() ?: SettingsEntity()
        dao.upsert(transform(current))
    }

    private fun SettingsEntity?.toSettings(): AppSettings =
        if (this == null) AppSettings()
        else AppSettings(
            notificationsEnabled = notificationsEnabled,
            calendarSyncEnabled = calendarSyncEnabled,
            onboardingComplete = onboardingComplete,
            reminderLeadMinutes = reminderLeadMinutes,
            autoCompleteEnabled = autoCompleteEnabled,
            themeMode = themeMode,
            defaultLessonMinutes = defaultLessonMinutes,
            currency = currency,
        )
}
