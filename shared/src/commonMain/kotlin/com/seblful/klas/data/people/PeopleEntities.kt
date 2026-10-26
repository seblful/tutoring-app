package com.seblful.klas.data.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.domain.model.ClassMode
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
