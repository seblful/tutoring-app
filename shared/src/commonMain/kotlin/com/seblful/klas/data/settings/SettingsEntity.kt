package com.seblful.klas.data.settings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.model.WeekStartChoice
import com.seblful.klas.domain.reminders.Reminders
import kotlinx.datetime.isoDayNumber

/**
 * App-wide settings. A single row (always [SINGLETON_ID]); device-local, so it is intentionally
 * not part of the backup snapshot.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    /**
     * Whether a synced class event invites the student as an attendee, so their calendar account
     * emails them about the lesson. Only has an effect while [calendarSyncEnabled] — the invitation
     * rides on the event.
     *
     * **Off by default**: mailing someone else's inbox is not something the app should start doing
     * on the tutor's behalf. Switching calendar sync on puts the lessons in *their* calendar and
     * nothing more; reaching the students is a separate, deliberate opt-in via Settings.
     *
     * The declared `defaultValue` stays **1** on purpose while the Kotlin default is `false`, and the
     * disagreement is the point. It is part of the v1 baseline schema rather than of any migration, so
     * what it governs is a table *created* with it: "on" is what calendar sync already did before this
     * became its own switch, and a column that defaulted to 0 would silently withdraw invitations those
     * students already have. A fresh install never sees it — rows are always written whole from this
     * class, so it is the Kotlin default that a new tutor starts on. Where a column added by a migration
     * wants the two to agree instead, see [dynamicColorEnabled].
     */
    @ColumnInfo(defaultValue = "1")
    val emailInvitesEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    /** Minutes before a class that reminders fire (in-app and email). */
    val reminderLeadMinutes: Int = Reminders.DEFAULT_LEAD_MINUTES,
    /**
     * Whether the morning digest naming the day's lessons is posted, and at what minute past local
     * midnight (540 = 09:00). **Off by default** — a reminder ahead of a lesson is what the tutor came
     * for; a message every morning is something they have to ask for.
     *
     * The five columns below are one feature and were added together by the v4 → v5 migration, whose
     * `DEFAULT`s these `defaultValue`s must match exactly or Room's schema validation fails the moment
     * the upgrade finishes. They agree on purpose, as [dynamicColorEnabled] does: an install that has
     * never had digests should not acquire one by being upgraded.
     */
    @ColumnInfo(defaultValue = "0")
    val dailyAgendaEnabled: Boolean = false,

    @ColumnInfo(defaultValue = "540")
    val agendaMinuteOfDay: Int = SettingsDefaults.AGENDA_MINUTE_OF_DAY,

    @ColumnInfo(defaultValue = "0")
    val debtDigestEnabled: Boolean = false,

    /** [java.time.DayOfWeek.getValue] — 1 is Monday. */
    @ColumnInfo(defaultValue = "1")
    val debtDayOfWeek: Int = SettingsDefaults.DEBT_DAY.isoDayNumber,

    @ColumnInfo(defaultValue = "540")
    val debtMinuteOfDay: Int = SettingsDefaults.DEBT_MINUTE_OF_DAY,
    /**
     * Whether the evening digest naming the day's **unfinished** lessons is posted, and at what minute
     * past local midnight (1260 = 21:00).
     *
     * Added by the v6 → v7 migration, whose `DEFAULT`s these `defaultValue`s must match exactly. **Off by
     * default**, like the two digests above and for the same reason: a notification the tutor did not ask
     * for is one the app should not start sending, and an install being upgraded has never had this one.
     *
     * 21:00 rather than a later hour because it is the end of a tutoring day rather than the end of the
     * day: lessons run into the evening, and a digest at midnight would be read the next morning about
     * yesterday. It is the tutor's to move, which is the whole reason it is a column.
     */
    @ColumnInfo(defaultValue = "0")
    val unfinishedDigestEnabled: Boolean = false,

    @ColumnInfo(defaultValue = "1260")
    val unfinishedMinuteOfDay: Int = SettingsDefaults.UNFINISHED_MINUTE_OF_DAY,
    /**
     * Whether finished classes auto-complete (and bill) without manual confirmation. **Off by
     * default**: billing a class the tutor hasn't confirmed risks over-stating a student's debt, so
     * the safe default is to have the tutor mark each lesson completed. Opt in via Settings.
     */
    val autoCompleteEnabled: Boolean = false,
    val themeMode: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Whether the colour scheme is derived from the wallpaper (Material You) instead of the app's own
     * indigo and teal. **Off by default**, which is the decision rather than the absence of one: the
     * palette is chosen, the status colours are tuned against it, and a tutor who never opens Settings
     * should get the app as it was designed. Android 12+ only, and Settings hides the row below that.
     *
     * The `defaultValue` matches the v3 → v4 migration's `DEFAULT 0` — they have to, or Room's schema
     * validation fails after the upgrade — and here the two agree on purpose rather than by coincidence
     * (contrast [emailInvitesEnabled], where they deliberately differ): an existing install has been
     * looking at the curated palette all along, so "off" is both the honest backfill and the right start.
     */
    @ColumnInfo(defaultValue = "0")
    val dynamicColorEnabled: Boolean = false,
    /** Pre-fills the end time when booking a class. */
    val defaultLessonMinutes: Int = SettingsDefaults.LESSON_MINUTES,
    /**
     * Pre-fills the hourly rate of a **newly created group**, in minor units. A tutor's group rate is
     * usually the same for every group, so typing it once here saves repeating it — and nothing else
     * reads this, so changing it never re-prices a group that already exists.
     *
     * **0 means "not set"**: the new-group form then starts with an empty price field the tutor has
     * to fill, rather than being pre-filled with an invented amount. (A group priced at 0 on purpose
     * is still expressible — the tutor types it.)
     */
    @ColumnInfo(defaultValue = "0")
    val defaultGroupPriceMinor: Long = 0,
    /** Currency used to display all money, stored as its code, e.g. "BYN" or "USD". */
    val currency: Currency = Currency.LEGACY,
    /**
     * Which language the app is shown in, stored as an [AppLanguage] name — "SYSTEM" follows the phone.
     *
     * Added by the v5 → v6 migration, whose `DEFAULT 'SYSTEM'` this `defaultValue` must match exactly or
     * Room's schema validation fails the moment the upgrade finishes. The two agree on purpose, as
     * [dynamicColorEnabled] does and unlike [emailInvitesEnabled]: "SYSTEM" is the honest backfill *and* the
     * right start. An install upgrading from v5 has only ever been shown Russian, and on a Russian phone —
     * which is nearly all of them, the app having shipped in Russian only — "SYSTEM" resolves to exactly
     * that. Pinning `'RUSSIAN'` instead would have been the same words today and the wrong ones forever
     * after, since it would state a preference the tutor never expressed and outlive them changing their
     * phone's language.
     */
    @ColumnInfo(defaultValue = "SYSTEM")
    val language: AppLanguage = AppLanguage.SYSTEM,
    /**
     * When the tutor last exported a backup, epoch millis — **null until they ever have.**
     *
     * Not a preference, and the only row in this table that is not one: it is a **receipt**. It lives
     * here because it is the same kind of fact everything else in this table is — device-local, and
     * deliberately outside the backup snapshot (`BackupSchemaCoverageTest`). Carrying it *in* a backup
     * would be circular: restoring last Tuesday's file would claim the last export happened last
     * Tuesday, on a phone that has never exported anything.
     *
     * Nullable rather than `0`-means-never, unlike [defaultGroupPriceMinor] next door. A sentinel earns
     * its keep where the column is `NOT NULL` for a reason; here the absence is the whole point of the
     * value — "you have never made a copy of this" is the strongest thing the backup screen can say —
     * and a nullable column carries it without a rule to remember.
     */
    val lastExportMillis: Long? = null,
    /**
     * The five [Privacy] choices, added by v10 → v11. Every `defaultValue` matches the migration's
     * `DEFAULT` and the private start — an upgraded install is as closed as a new one.
     */
    @ColumnInfo(defaultValue = "0")
    val lockScreenDetails: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lockScreenWidgets: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val appLock: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val relockAfterMinutes: Int = Privacy.DEFAULT_RELOCK_MINUTES,
    @ColumnInfo(defaultValue = "0")
    val hideInRecents: Boolean = false,
    /** The weekly backup's folder (a SAF tree URI), null while it is off. Added by v11 → v12. */
    val autoBackupFolder: String? = null,
    /** Whether the last weekly copy could not be written — the folder has to be picked again. */
    @ColumnInfo(defaultValue = "0")
    val autoBackupFailed: Boolean = false,
    /**
     * The guide a new tutor gets (`Guide`): the [com.seblful.klas.domain.guide.Tip]s seen on this phone, as
     * their names joined by commas, and whether the first-steps card is put away. Added by v13 → v14, which
     * marks both done for an install already through setup — the guide is for someone new to the app.
     */
    @ColumnInfo(defaultValue = "")
    val tipsSeen: String = "",
    @ColumnInfo(defaultValue = "0")
    val firstStepsHidden: Boolean = false,
    /**
     * What this phone is still to be given after a restore (`PhoneSetup`): the
     * [com.seblful.klas.domain.guide.PhoneStep]s the backup's phone had on, as names joined by commas; empty
     * when there is no card. Added by v14 → v15.
     */
    @ColumnInfo(defaultValue = "")
    val phoneSetup: String = "",
    /** Where the schedule's weeks start. Added by v15 → v16. */
    @ColumnInfo(defaultValue = "PHONE")
    val weekStart: WeekStartChoice = WeekStartChoice.PHONE,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
