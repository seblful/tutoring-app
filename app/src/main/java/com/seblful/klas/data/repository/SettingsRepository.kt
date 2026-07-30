package com.seblful.klas.data.repository

import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
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
    /** Pre-fills a new group's hourly rate. Null while the tutor has not set one. */
    val defaultGroupPrice: Money? = null,
    val currency: Currency = Currency.BYN,
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
     * the student is invited, how far ahead the event's email reminder fires — means re-earning the
     * copy. Switching sync **off** withdraws the events instead; with sync off nothing the mirror
     * would read is observable, and there is nothing to re-earn.
     */
    val calendarWork: CalendarWork
        get() = when {
            moved { it.calendarSyncEnabled } && !after.calendarSyncEnabled -> CalendarWork.WITHDRAW
            after.calendarSyncEnabled &&
                (
                    moved { it.calendarSyncEnabled } ||
                        moved { it.emailInvitesEnabled } ||
                        moved { it.reminderLeadMinutes }
                    ) -> CalendarWork.RESYNC
            else -> CalendarWork.NONE
        }

    /**
     * Whether the in-app reminder plan must be re-derived: every armed alarm's firing instant was
     * computed under the old lead. Independent of calendar sync — the alarms are the app's own.
     */
    val replansReminders: Boolean get() = moved { it.reminderLeadMinutes }

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
            SettingsChange(before, after)
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
            // The column's 0-means-not-set sentinel is spelled here, at the row's edge, once —
            // it used to be restated by every reader of the raw minor units.
            defaultGroupPrice = Money(defaultGroupPriceMinor).takeIf { defaultGroupPriceMinor != 0L },
            currency = Currency.parse(currency),
            lastExportAt = lastExportMillis?.let(Instant::ofEpochMilli),
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
        defaultGroupPriceMinor = defaultGroupPrice?.minor ?: 0,
        currency = currency.name,
        lastExportMillis = lastExportAt?.toEpochMilli(),
        // `SettingsEntity.showGroupsTab` is deliberately not set: nothing reads it any more (groups
        // and students share one tab now), and the column stays only because dropping it would mean
        // a table rebuild — SQLite before 3.35 has no `DROP COLUMN`, and minSdk 24 reaches back well
        // before that. It keeps its Kotlin default. See the entity for the whole note.
    )
}
