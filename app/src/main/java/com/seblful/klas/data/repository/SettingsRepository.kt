package com.seblful.klas.data.repository

import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.format.AppLocale
import com.seblful.klas.format.localeFor
import com.seblful.klas.format.regionCurrencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
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
    /**
     * What time of day the agenda is posted, as **minutes since local midnight** — 540 is 09:00.
     *
     * Minutes rather than a `LocalTime`, because this is a stored column and `AppSettings` is the shape of
     * the row: an `Int` needs no converter and no total parse function on the way back (contrast
     * `EnumConverters`, which exists because a *name* can come back unreadable). The minutes are turned
     * into a wall-clock time by `domain/reminders/Digests`, which is also where "what if that time is already past
     * today" is answered.
     */
    val agendaMinuteOfDay: Int = 9 * 60,
    /** Whether the weekly summary of who owes money is posted. Off by default, as [dailyAgendaEnabled]. */
    val debtDigestEnabled: Boolean = false,
    /**
     * Which day of the week the debt digest lands on, as [java.time.DayOfWeek.getValue] — 1 is Monday,
     * which is the default and the day a tutor's week is planned on.
     *
     * Deliberately **not** derived from `di.FirstDayOfWeek`: that answers where a *calendar grid* starts,
     * a question about how the phone's owner reads a month. This is a day the tutor picked to be told
     * something on, and the two would only coincide by accident.
     */
    val debtDayOfWeek: Int = 1,
    /** What time the debt digest is posted, minutes since local midnight. See [agendaMinuteOfDay]. */
    val debtMinuteOfDay: Int = 9 * 60,
    /**
     * Whether the evening digest naming the day's **unfinished** lessons is posted — the lessons that
     * have finished and that nobody has said happened or not ([com.seblful.klas.domain.lesson.LessonPhase.OVERDUE]).
     *
     * Off by default, as [dailyAgendaEnabled]. It is the counterpart of [autoCompleteEnabled] rather than
     * a companion to it: with auto-completion on there is nothing left unfinished by the time this fires,
     * so the two are the app's two answers to "the lesson ended and nobody said anything" — assume it
     * happened, or ask. Both may be on; the digest then simply has nothing to post.
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
     *
     * Like [themeMode] beside it, this owes nothing outside the row and so has no arm in
     * [SettingsChange]: it changes what the next frame is painted in and nothing that was recorded.
     */
    val dynamicColorEnabled: Boolean = false,
    val defaultLessonMinutes: Int = 60,
    /** Pre-fills a new group's hourly rate. Null while the tutor has not set one. */
    val defaultGroupPrice: Money? = null,
    /**
     * What money is captioned with. **The default here is only the shape of the row** — what a phone that
     * has chosen nothing actually shows is [Currency.preferredFor]'s answer, substituted by
     * [SettingsRepository] where a missing row is mapped to defaults, because it is a fact about the device
     * rather than a constant. BYN is simply the first currency the app ever had.
     */
    val currency: Currency = Currency.BYN,
    /**
     * Which language the app is shown in. [AppLanguage.SYSTEM] — follow the phone — is the default and a
     * real answer, so unlike [currency] there is nothing about the device to substitute: the resolution
     * happens where it is displayed, and keeps happening if the tutor changes their phone.
     */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /**
     * When a backup was last exported — **null when one never has been.**
     *
     * The only member of this class that is not a preference, and the reason it is here rather than in a
     * table of its own is that it shares every property of one: a single device-local value, deliberately
     * outside the backup. See `SettingsEntity.lastExportMillis`.
     *
     * It owes the world nothing when it changes, which is why it needs no named mutator and no arm in
     * [SettingsChange]: the export already happened, and recording that fact cannot re-earn a calendar or
     * re-plan a reminder.
     */
    val lastExportAt: Instant? = null,
)

/**
 * The move one [SettingsRepository.update] made — the row as it was and as it is now — with what
 * the move **owes the world outside the row** classified here, beside the fields it is about.
 *
 * Two settings used to have named view-model methods whose bodies were the store plus its tail,
 * and the rest rode a generic path with no tail at all. Which settings owe something was recorded
 * only in which methods happened to exist — and the one that was missed left every already-mirrored
 * calendar event carrying a stale reminder lead indefinitely. A classification handed back by the
 * store itself cannot be forgotten per setting; the discharging stays with the caller, because the
 * mirror this row gates reads the row (`CalendarMirror` depends on [SettingsRepository]).
 */
data class SettingsChange(val before: AppSettings, val after: AppSettings) {

    /** What the calendar mirror is owed. */
    enum class CalendarWork { NONE, RESYNC, WITHDRAW }

    /**
     * The calendar work this change owes. The mirror is a best-effort copy of local truth filtered
     * through these settings, so moving one the mirror bakes into every event — sync itself, whether
     * the student is invited, how far ahead the event's email reminder fires, **the words it is written
     * in** — means re-earning the copy. Switching sync **off** withdraws the events instead; with sync off
     * nothing the mirror would read is observable, and there is nothing to re-earn.
     *
     * The language is in that list for the same reason a rename is (`CalendarMirror.refreshFor*`): a
     * mirrored event's location is the app's own word for «Дистанционно» or "Remote", written at mirror time,
     * so every event already in the tutor's calendar keeps the language it was written in until something
     * re-mirrors it. Without this arm, switching to English left a calendar half in Russian indefinitely —
     * the exact shape of the defect this class was written about, one setting whose tail nobody ran.
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
     *
     * All seven in one arm rather than one arm each, because they discharge to the same call and splitting
     * them would only invite a caller to discharge half. Switching a digest **off** counts too: the work
     * already enqueued would otherwise still fire, and a notification arriving after the switch that
     * governs it was turned off is the same class of defect as [sweepsDueLessons] — a setting whose tail
     * nobody ran.
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
     *
     * The third obligation, and the one that was missing — with exactly the consequence this class was
     * written about. Nothing discharged the switch, so the backlog sat until something unrelated
     * happened to run the upkeep pass: an app restart, the half-hourly tick, or *any* lesson write. The
     * switch appeared to do nothing, and then confirming a single old lesson by hand completed every
     * other old lesson at once — the tutor's tap on one row credited months of income, because it was
     * the tap that finally ran the pass owed since the toggle.
     *
     * Only on the way on. Turning it **off** owes nothing: the pass simply stops running, and lessons
     * already completed stay completed — un-billing the past is not what switching a preference off
     * means.
     */
    val sweepsDueLessons: Boolean get() = moved { it.autoCompleteEnabled } && after.autoCompleteEnabled

    private fun <T> moved(field: (AppSettings) -> T): Boolean = field(before) != field(after)
}

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
    /**
     * The phone's locales — what a row that has chosen nothing falls back to, and what
     * [AppLanguage.SYSTEM] is resolved against. See `di.DeviceLocales`.
     */
    @param:com.seblful.klas.di.DeviceLocales private val deviceLocales: List<Locale>,
) {
    fun observe(): Flow<AppSettings> = dao.observe().map { it.toSettings() }

    suspend fun get(): AppSettings = dao.get().toSettings()

    /**
     * Applies [transform] to the current settings and stores the result. Read-modify-write against
     * the single row, so a caller changes one field with `update { it.copy(currency = Currency.RUB) }`
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
     *
     * Returns the move it made, with its obligations classified — see [SettingsChange].
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): SettingsChange =
        writeLock.withLock {
            val before = get()
            val after = transform(before)
            dao.upsert(after.toEntity())
            // A write publishes the display locale too. The `get` above published the row as it *was*, so
            // without this a caller that changes the language and then formats something — a worker, or the
            // settings screen's own tail — would be handed the previous one until the flow next emitted.
            SettingsChange(before, resolved(after))
        }

    private val writeLock = Mutex()

    /**
     * The row as the rest of the app speaks it — and the one place the app's **display locale** is
     * published.
     *
     * Every reader of the settings goes through here (the UI, the workers, the notifier), so resolving
     * `language` on the way out is what makes it impossible to be handed a new language and then format a
     * date in the previous one: `AppLocale.current` is already the new one before this `AppSettings` reaches
     * anybody. Doing it in the view model instead would have left the notification tray and every worker
     * reading a locale nothing had updated, and doing it in two places would have made *which* collector ran
     * first decide what month a lesson was in. See [AppLocale].
     */
    private fun SettingsEntity?.toSettings(): AppSettings = resolved(
        // A row that does not exist is a tutor who has chosen nothing, which is not the same as a tutor who
        // chose the app's oldest currency: the phone names one, so that is what their money is captioned
        // with until they say otherwise, and the first settings write persists it. The `AppSettings`
        // defaults state the shape of the row; this states what an unwritten one means on *this* device.
        if (this == null) AppSettings(currency = Currency.preferredFor(deviceLocales.regionCurrencies()))
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

    /** [settings], with its language published as the locale everything displays in. */
    private fun resolved(settings: AppSettings): AppSettings = settings.also {
        AppLocale.current = it.language.localeFor(deviceLocales)
    }

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
