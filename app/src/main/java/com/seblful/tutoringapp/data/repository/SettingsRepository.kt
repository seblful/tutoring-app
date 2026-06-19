package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.data.local.SettingsDao
import com.seblful.tutoringapp.data.local.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App settings (notifications, calendar sync, onboarding). A missing row maps to defaults. */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
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

    private suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
        val current = dao.get() ?: SettingsEntity()
        dao.upsert(transform(current))
    }

    private fun SettingsEntity?.toSettings(): AppSettings =
        if (this == null) AppSettings()
        else AppSettings(notificationsEnabled, calendarSyncEnabled, onboardingComplete)
}
