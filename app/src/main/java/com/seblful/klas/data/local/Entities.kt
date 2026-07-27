package com.seblful.klas.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val defaultMode: String = "IN_PERSON",
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
 * A group **prices** its lessons but does not owe for them: booking one writes one [ClassEntity] per
 * member, each snapshotting [priceMinor], so balances and reports stay strictly per-student. Groups
 * are archive-only once they have classes (see `GroupRepository.delete`), like students.
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
    val defaultMode: String = "IN_PERSON",
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
 * A subject or group that can still be deleted (one with no classes) takes its rate log with it,
 * rather than leaving rows describing a rate for something that no longer exists. Income is what
 * gets `RESTRICT`, and it lives on [ClassEntity].
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

@Serializable
@Entity(
    tableName = "classes",
    indices = [
        Index("studentId"), Index("subjectId"), Index("startMillis"), Index("status"), Index("groupId"),
    ],
    // RESTRICT, not CASCADE: a class is recognized income and history. The schema itself must refuse
    // to delete a student, subject or group that still has classes, so no future code path can
    // silently erase earned income by removing a parent. Subjects and groups are additionally
    // guarded at their repositories; students have no delete path at all (archive only).
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val priceMinor: Long = 0,
    val status: String,
    val chargeMinor: Long = 0,
    /**
     * The group this class was booked as part of, or null for an individual class. The members'
     * classes of one group lesson share this id *and* [startMillis] — that pair is the "session"
     * the schedule collapses into a single card.
     */
    val groupId: Long? = null,
    val mode: String = "IN_PERSON",
    val calendarEventId: String? = null,
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
     * The declared `defaultValue` stays **1** on purpose, in step with the v2 → v3 migration's
     * `DEFAULT 1` (they must match, or Room's schema validation fails). That pair governs only the
     * upgrade backfill, where "on" is what calendar sync already did before this became its own
     * switch — backfilling 0 would silently withdraw invitations those students already have. A
     * fresh install never sees it: rows are always written whole from this class, so it is this
     * Kotlin default that a new tutor starts on.
     */
    @ColumnInfo(defaultValue = "1")
    val emailInvitesEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    /** Minutes before a class that reminders fire (in-app and email). */
    val reminderLeadMinutes: Int = 60,
    /**
     * Whether finished classes auto-complete (and bill) without manual confirmation. **Off by
     * default**: billing a class the tutor hasn't confirmed risks over-stating a student's debt, so
     * the safe default is to have the tutor mark each lesson completed. Opt in via Settings.
     */
    val autoCompleteEnabled: Boolean = false,
    /** "SYSTEM" | "LIGHT" | "DARK". */
    val themeMode: String = "SYSTEM",
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
    /** Currency code used to display all money, e.g. "BYN" or "RUB". */
    val currency: String = "BYN",
    /**
     * **Vestigial — written with its default, read by nothing.**
     *
     * It used to decide whether «Группы» had a tab of its own. Students and groups now share one
     * («Люди»), so there is no longer a tab to hide and the setting has no meaning. The *column*
     * stays because removing it is not free: SQLite gained `ALTER TABLE … DROP COLUMN` only in 3.35,
     * which is Android 12, and this app supports API 24 — so a real removal means rebuilding the
     * table under a migration, which is a genuine risk to a tutor's data in exchange for one unread
     * boolean. [com.seblful.klas.data.repository.AppSettings] has dropped it, so nothing above this
     * layer can read it by accident.
     */
    val showGroupsTab: Boolean = true,
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
