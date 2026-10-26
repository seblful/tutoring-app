package com.seblful.klas.data.lessons

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.serialization.Serializable

/**
 * One lesson: an hour of the tutor's time, for one student or for a group.
 *
 * **The lesson is a row**, not a *grouping* of the classes sharing a `(groupId, startMillis)` pair — that
 * would make "these rows are one lesson" an invariant no schema can state and every write path has to
 * maintain: move one member's row and the lesson silently splits in two, each then competing for the same
 * hour. A lesson has an id, [AttendanceEntity] points at it, and moving one is one update.
 *
 * **[status] is the lesson's, and only the lesson's.** Whether the hour happened is a fact about the
 * hour; whether a given student was in it is [AttendanceEntity.state]. One enum answering both would
 * leave a group lesson with no status of its own.
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
 * no-show a fact the app can hold without cancelling the student's class, which would zero the charge and,
 * on a group lesson, leave the lesson itself with no single status.
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
