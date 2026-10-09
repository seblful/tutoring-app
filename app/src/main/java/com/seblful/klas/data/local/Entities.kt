package com.seblful.klas.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.reminders.Reminders
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val defaultMode: ClassMode = ClassMode.IN_PERSON,
    val archived: Boolean = false,
)

/** Which subjects a student takes. */
@Serializable
@Entity(
    tableName = "student_subjects",
    primaryKeys = ["studentId", "subjectId"],
    indices = [Index("subjectId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class StudentSubjectCrossRef(
    val studentId: Long,
    val subjectId: Long,
)

/**
 * A named set of students taught together, e.g. «11 «А», вечер», at one rate for the whole group.
 *
 * A group **prices** its lessons but does not owe for them: booking one writes a [LessonEntity] and an
 * [AttendanceEntity] per member, each snapshotting [priceMinor], so balances and reports stay strictly
 * per-student. Groups are archive-only once they have lessons (see `GroupRepository.delete`).
 */
@Serializable
@Entity(
    tableName = "student_groups",
    indices = [Index("subjectId")],
    foreignKeys = [
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    /**
     * What the group is taught, fixed when the group is made rather than chosen per lesson: its
     * lessons are all this subject, and only students priced for it can be added to the roster.
     *
     * **Nullable, because a restore cannot invent one.** A group imported from a backup written
     * before the format carried a subject (`BackupData` v3 and older) has none to backfill — its
     * members may not even share one — so it stays null and keeps the looser behaviour: any student
     * may be a member, and the subject is picked when booking.
     * Setting a subject is what opts a group into the stricter rules, and is one-way in practice
     * since [GroupRepository.save] then drops members who are not priced for it.
     *
     * `RESTRICT` on delete: a subject that a group is taught cannot be deleted out from under it.
     */
    val subjectId: Long? = null,
    /**
     * The group's **hourly rate** in minor units — what each member of one of its lessons is billed,
     * regardless of their individual [PriceEntity] rows. Added in v5, backfilled to 0 (the migration's
     * `DEFAULT 0` must stay in step with the declared `defaultValue`, or Room's validation fails):
     * a group that predates its own rate is left explicitly free until the tutor prices it, rather
     * than being given an invented number.
     */
    @ColumnInfo(defaultValue = "0")
    val priceMinor: Long = 0,
    /** Pre-fills the mode of a lesson booked for this group. */
    val defaultMode: ClassMode = ClassMode.IN_PERSON,
    val archived: Boolean = false,
)

/** Who belongs to a group. */
@Serializable
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "studentId"],
    indices = [Index("studentId")],
    foreignKeys = [
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class GroupMemberCrossRef(
    val groupId: Long,
    val studentId: Long,
)

/** Current price for one (student, subject). Completed classes snapshot their own charge. */
@Serializable
@Entity(
    tableName = "prices",
    primaryKeys = ["studentId", "subjectId"],
    indices = [Index("subjectId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PriceEntity(
    val studentId: Long,
    val subjectId: Long,
    val amountMinor: Long,
)

/**
 * One recorded change of an hourly rate — a student's price for a subject, or a group's own rate.
 *
 * **Two shapes in one table, on purpose.** A row is either about a (student, subject) price or about
 * a group's rate, so exactly one of `(studentId, subjectId)` / `groupId` is set and the other is
 * null. Splitting it into two tables would double the DAO, the backup entry and the UI for what the
 * tutor reads as one question ("what did this cost before?"), so the discrimination is done once, on
 * the way out of storage: [com.seblful.klas.domain.model.RateOwner] is the sealed domain shape and
 * `Mappers.kt` is the only place these nullable columns are interpreted.
 *
 * `CASCADE` on all three parents, matching [PriceEntity]: this is a log *about* a price, not income.
 * A subject or group that can still be deleted (one with no lessons) takes its rate log with it,
 * rather than leaving rows describing a rate for something that no longer exists. Income is what
 * gets `RESTRICT`, and it lives on [AttendanceEntity].
 */
@Serializable
@Entity(
    tableName = "price_history",
    indices = [Index("studentId"), Index("subjectId"), Index("groupId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RateChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Set together with [subjectId] for an individual price; null for a group's rate. */
    val studentId: Long? = null,
    val subjectId: Long? = null,
    /** Set for a group's own rate; null for an individual price. */
    val groupId: Long? = null,
    /** The rate before the change, or null when this row is where the rate started. */
    val fromMinor: Long? = null,
    val toMinor: Long,
    /**
     * When the change was recorded — **null for a rate that predates the log**. Nothing writes such
     * a row today, but a restored backup can carry one, so the ordering still has to hold. See
     * [com.seblful.klas.domain.model.RateChange.at]: the amount is known, the date is not, and
     * `ORDER BY atMillis DESC` sorts these last, since SQLite treats NULL as the smallest value.
     */
    val atMillis: Long? = null,
)

/**
 * One lesson: an hour of the tutor's time, for one student or for a group.
 *
 * **The lesson is a row.** It used to be a *grouping* — the classes sharing a `(groupId, startMillis)`
 * pair — which made "these rows are one lesson" an invariant no schema could state and every write path
 * had to maintain: move one member's row and the lesson silently split in two, each then competing for
 * the same hour. Here a lesson has an id, [AttendanceEntity] points at it, and moving one is one update.
 *
 * **[status] is the lesson's, and only the lesson's.** Whether the hour happened is a fact about the
 * hour; whether a given student was in it is [AttendanceEntity.state]. One enum answered both before,
 * which is why a group lesson had no status of its own and had to derive one.
 *
 * `RESTRICT` on both parents: a lesson is history, so the schema itself refuses to delete a subject or
 * a group that still has lessons. The rate is not here — see [AttendanceEntity.priceMinor].
 */
@Serializable
@Entity(
    tableName = "lessons",
    indices = [Index("subjectId"), Index("startMillis"), Index("status"), Index("groupId")],
    foreignKeys = [
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val status: LessonStatus,
    /** The group whose lesson this is, or null for one student's. */
    val groupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
)

/**
 * One student in one lesson: whether they took part, and what it cost them.
 *
 * A group lesson has a row of these per member; an individual lesson has exactly one. Both bill per
 * student — a group decides *how much*, never *who owes it* — which is why [priceMinor] and
 * [chargeMinor] live here and not on the lesson. Only a lesson restored from a pre-v3 backup can hold
 * members at differing rates; keeping the rate per student is what lets that history stay true.
 *
 * [state] is the attendance: expected, present, absent, or absent-and-billed-anyway. It is what makes a
 * no-show a fact the app can hold — the previous model could only express it by cancelling the student's
 * class, which zeroed the charge and, on a group lesson, left the lesson itself with no single status.
 *
 * `CASCADE` on the lesson (a lesson's members go with it — the lesson is the unit of deletion) and
 * `RESTRICT` on the student, who cannot be deleted while any lesson still bills them.
 */
@Serializable
@Entity(
    tableName = "attendance",
    indices = [Index("lessonId"), Index("studentId"), Index("state")],
    foreignKeys = [
        ForeignKey(LessonEntity::class, ["id"], ["lessonId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val studentId: Long,
    val state: Attended,
    /** The **hourly rate** this student was booked at, snapshotted so a later re-pricing cannot rewrite it. */
    val priceMinor: Long = 0,
    val chargeMinor: Long = 0,
    val calendarEventId: String? = null,
)

/**
 * One piece of homework: what was set, for whom, from when, and when it was done.
 *
 * **For one student or for a whole group** — exactly one of [studentId] and [groupId] is set, the pair
 * [RateChangeEntity] uses for the same two owners. A group's is one row, marked done once, rather than a
 * copy per member. `Mappers.kt` reads the pair back as a sealed `Assignee`.
 *
 * **Not tied to a lesson by any column.** The lesson it is checked at is derived: whoever it is for, their
 * next lesson after [givenAtMillis] (`domain/homework/HomeworkRules`). The next lesson is often not booked when
 * homework is set, and can move or be cancelled once it is; a stored link would need a writer at each of
 * those, where a derived one follows them for free.
 *
 * `CASCADE` on both owners: homework is not billed history, so it does not pin a group (deletable while it
 * has no lessons) in place. Students are never deleted.
 *
 * @param doneAtMillis when the tutor marked it done, or null while it is current — done is the whole of
 *   its state, and the archive is the done ones.
 */
@Serializable
@Entity(
    tableName = "homework",
    indices = [Index("studentId"), Index("groupId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long? = null,
    val groupId: Long? = null,
    val text: String,
    val givenAtMillis: Long,
    val doneAtMillis: Long? = null,
)

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
    val agendaMinuteOfDay: Int = 9 * 60,

    @ColumnInfo(defaultValue = "0")
    val debtDigestEnabled: Boolean = false,

    /** [java.time.DayOfWeek.getValue] — 1 is Monday. */
    @ColumnInfo(defaultValue = "1")
    val debtDayOfWeek: Int = 1,

    @ColumnInfo(defaultValue = "540")
    val debtMinuteOfDay: Int = 9 * 60,
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
    val unfinishedMinuteOfDay: Int = 21 * 60,
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
    val defaultLessonMinutes: Int = 60,
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
    val currency: Currency = Currency.BYN,
    /**
     * Which language the app is shown in, stored as its name — "SYSTEM" | "RUSSIAN" | "ENGLISH".
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
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Serializable
@Entity(
    tableName = "payments",
    indices = [Index("studentId")],
    // RESTRICT: a payment is part of the financial record; the DB must refuse to delete a student who
    // still has payments rather than cascade them away as silent loss. (Students are archive-only.)
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val dateMillis: Long,
    val amountMinor: Long,
)
