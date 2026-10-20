package com.seblful.klas.data.settings

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.settings.DevicePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/** App settings. A missing row maps to defaults. */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val emailInvitesEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val reminderLeadMinutes: Int = Reminders.DEFAULT_LEAD_MINUTES,
    /**
     * Whether the morning digest naming the day's lessons is posted. **Off by default**, like every other
     * notification the app sends unasked: a reminder before a lesson is the feature the tutor installed
     * this for, a message every morning is one they have to want.
     */
    val dailyAgendaEnabled: Boolean = false,
    /** What time of day the agenda is posted, as **minutes since local midnight** — 540 is 09:00. */
    val agendaMinuteOfDay: Int = 9 * 60,
    /** Whether the weekly summary of who owes money is posted. Off by default, as [dailyAgendaEnabled]. */
    val debtDigestEnabled: Boolean = false,
    /**
     * Which day of the week the debt digest lands on, as [java.time.DayOfWeek.getValue] — 1 is Monday,
     * which is the default and the day a tutor's week is planned on.
     */
    val debtDayOfWeek: Int = 1,
    /** What time the debt digest is posted, minutes since local midnight. See [agendaMinuteOfDay]. */
    val debtMinuteOfDay: Int = 9 * 60,
    /**
     * Whether the evening digest naming the day's **unfinished** lessons is posted — the lessons that
     * have finished and that nobody has said happened or not ([com.seblful.klas.domain.lesson.LessonPhase.OVERDUE]).
     */
    val unfinishedDigestEnabled: Boolean = false,
    /**
     * What time the evening digest is posted, minutes since local midnight — 1260 is 21:00. See
     * [agendaMinuteOfDay] for why this is an `Int`.
     */
    val unfinishedMinuteOfDay: Int = 21 * 60,
    val autoCompleteEnabled: Boolean = false,
    val themeMode: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Whether to take the colour scheme from the wallpaper instead of the app's own palette. Off by
     * default — see `SettingsEntity.dynamicColorEnabled` for why that is a decision and not a gap.
     */
    val dynamicColorEnabled: Boolean = false,
    val defaultLessonMinutes: Int = 60,
    /** Pre-fills a new group's hourly rate. Null while the tutor has not set one. */
    val defaultGroupPrice: Money? = null,
    /**
     * What money is captioned with. **The default here is only the shape of the row** — what a phone that
     * has chosen nothing actually shows is [Currency.preferredFor]'s answer, substituted by
     * [SettingsRepository] where a missing row is mapped to defaults, because it is a fact about the device
     * rather than a constant.
     */
    val currency: Currency = Currency.BYN,
    /**
     * Which language the app is shown in. [AppLanguage.SYSTEM] — follow the phone — is the default and a
     * real answer, so unlike [currency] there is nothing about the device to substitute: the resolution
     * happens where it is displayed, and keeps happening if the tutor changes their phone.
     */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** When a backup was last exported — **null when one never has been.** */
    val lastExportAt: Instant? = null,
)

/**
 * The move one [SettingsRepository.update] made — the row as it was and as it is now — with what
 * the move **owes the world outside the row** classified here, beside the fields it is about.
 */
data class SettingsChange(val before: AppSettings, val after: AppSettings) {

    /** What the calendar mirror is owed. */
    enum class CalendarWork { NONE, RESYNC, WITHDRAW }

    /**
     * The calendar work this change owes. The mirror is a best-effort copy of local truth filtered
     * through these settings, so moving one the mirror bakes into every event — sync itself, whether
     * the student is invited, how far ahead the event's email reminder fires, **the words it is written
     * in** — means re-earning the copy.
     */
    val calendarWork: CalendarWork
        get() = when {
            moved { it.calendarSyncEnabled } && !after.calendarSyncEnabled -> CalendarWork.WITHDRAW
            after.calendarSyncEnabled &&
                (
                    moved { it.calendarSyncEnabled } ||
                        moved { it.emailInvitesEnabled } ||
                        moved { it.reminderLeadMinutes } ||
                        moved { it.language }
                    ) -> CalendarWork.RESYNC
            else -> CalendarWork.NONE
        }

    /**
     * Whether the in-app reminder plan must be re-derived: every armed alarm's firing instant was
     * computed under the old lead. Independent of calendar sync — the alarms are the app's own.
     */
    val replansReminders: Boolean get() = moved { it.reminderLeadMinutes }

    /**
     * Whether the digests have to be re-planned: each is a single piece of work enqueued for the next
     * time it is due, so **every** field that decides when — or whether — it fires invalidates what is
     * already enqueued.
     */
    val replansDigests: Boolean
        get() = moved { it.dailyAgendaEnabled } ||
            moved { it.agendaMinuteOfDay } ||
            moved { it.debtDigestEnabled } ||
            moved { it.debtDayOfWeek } ||
            moved { it.debtMinuteOfDay } ||
            moved { it.unfinishedDigestEnabled } ||
            moved { it.unfinishedMinuteOfDay }

    /**
     * Whether the lessons already waiting have to be settled now: turning auto-completion **on** owes
     * the sweep the tutor just asked for.
     */
    val sweepsDueLessons: Boolean get() = moved { it.autoCompleteEnabled } && after.autoCompleteEnabled

    private fun <T> moved(field: (AppSettings) -> T): Boolean = field(before) != field(after)
}

/** The single settings row. */
@SingleIn(AppScope::class)
class SettingsRepository @Inject constructor(
    private val dao: SettingsDao,

    private val devicePreferences: DevicePreferences,
) {
    private val _language = MutableStateFlow(AppLanguage.SYSTEM)

    /** Latest language read or written, for host formatting without a global locale mutation. */
    val language = _language.asStateFlow()

    fun observe(): Flow<AppSettings> = dao.observe().map { it.toSettings() }

    suspend fun get(): AppSettings = dao.get().toSettings()

    /**
     * Applies [transform] to the current settings and stores the result. Read-modify-write against
     * the single row, so a caller changes one field with `update { it.copy(currency = Currency.RUB) }`
     * and leaves the rest alone.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): SettingsChange =
        writeLock.withLock {
            val before = get()
            val after = transform(before)
            dao.upsert(after.toEntity())
            SettingsChange(before, resolved(after))
        }

    private val writeLock = Mutex()

    private fun SettingsEntity?.toSettings(): AppSettings = resolved(
        // A row that does not exist is a tutor who has chosen nothing, which is not the same as a tutor who
        // chose the app's oldest currency: the phone names one, so that is what their money is captioned
        // with until they say otherwise, and the first settings write persists it. The `AppSettings`
        // defaults state the shape of the row; this states what an unwritten one means on *this* device.
        if (this == null) AppSettings(currency = Currency.preferredFor(devicePreferences.currencyCodes))
        else AppSettings(
            notificationsEnabled = notificationsEnabled,
            calendarSyncEnabled = calendarSyncEnabled,
            emailInvitesEnabled = emailInvitesEnabled,
            onboardingComplete = onboardingComplete,
            reminderLeadMinutes = reminderLeadMinutes,
            dailyAgendaEnabled = dailyAgendaEnabled,
            agendaMinuteOfDay = agendaMinuteOfDay,
            debtDigestEnabled = debtDigestEnabled,
            debtDayOfWeek = debtDayOfWeek,
            debtMinuteOfDay = debtMinuteOfDay,
            unfinishedDigestEnabled = unfinishedDigestEnabled,
            unfinishedMinuteOfDay = unfinishedMinuteOfDay,
            autoCompleteEnabled = autoCompleteEnabled,
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            defaultLessonMinutes = defaultLessonMinutes,
            // The column's 0-means-not-set sentinel is spelled here, at the row's edge, once —
            // it used to be restated by every reader of the raw minor units.
            defaultGroupPrice = Money(defaultGroupPriceMinor).takeIf { defaultGroupPriceMinor != 0L },
            currency = currency,
            language = language,
            lastExportAt = lastExportMillis?.let { Instant.fromEpochMilliseconds(it) },
        ),
    )

    private fun resolved(settings: AppSettings): AppSettings = settings.also {
        _language.value = it.language
    }

    private fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
        notificationsEnabled = notificationsEnabled,
        calendarSyncEnabled = calendarSyncEnabled,
        emailInvitesEnabled = emailInvitesEnabled,
        onboardingComplete = onboardingComplete,
        reminderLeadMinutes = reminderLeadMinutes,
        dailyAgendaEnabled = dailyAgendaEnabled,
        agendaMinuteOfDay = agendaMinuteOfDay,
        debtDigestEnabled = debtDigestEnabled,
        debtDayOfWeek = debtDayOfWeek,
        debtMinuteOfDay = debtMinuteOfDay,
        unfinishedDigestEnabled = unfinishedDigestEnabled,
        unfinishedMinuteOfDay = unfinishedMinuteOfDay,
        autoCompleteEnabled = autoCompleteEnabled,
        themeMode = themeMode,
        dynamicColorEnabled = dynamicColorEnabled,
        defaultLessonMinutes = defaultLessonMinutes,
        defaultGroupPriceMinor = defaultGroupPrice?.minor ?: 0,
        currency = currency,
        language = language,
        lastExportMillis = lastExportAt?.toEpochMilliseconds(),
    )
}
