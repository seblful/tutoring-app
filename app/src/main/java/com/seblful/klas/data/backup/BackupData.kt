package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.serialization.Serializable

/**
 * One class as versions 1–5 wrote it: a row per student carrying the lesson's own facts alongside their
 * own. **Frozen.** It is the shape of files already on somebody's phone, so it can neither change nor go
 * away while those files are still meant to import — see [BackupData.classes] and [splitIntoLessons].
 */
@Serializable
data class LegacyClass(
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val priceMinor: Long = 0,
    val status: String,
    val chargeMinor: Long = 0,
    val groupId: Long? = null,
    val mode: String = "IN_PERSON",
    val calendarEventId: String? = null,
)

/**
 * A full snapshot of the database, serialized to JSON for backup/restore. Stores the raw
 * persisted rows verbatim (ids included) so importing into an empty database reproduces the
 * exact same data. [version] guards the format for future migrations.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val subjects: List<SubjectEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val studentSubjects: List<StudentSubjectCrossRef> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val groupMembers: List<GroupMemberCrossRef> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    /** The rate log (`price_history`) — the record of what prices used to be. */
    val rateHistory: List<RateChangeEntity> = emptyList(),
    /**
     * The lessons — **v6 and later**. Empty in an older file, whose lessons are inside [classes] instead.
     */
    val lessons: List<LessonEntity> = emptyList(),
    /** Who was in each lesson, and what it cost them — **v6 and later**. */
    val attendance: List<AttendanceEntity> = emptyList(),
    /**
     * The flat classes of **v1–v5**, and empty in anything this build writes. Kept because a backup file
     * is the only way a tutor's data crosses builds: an old file still has to restore, and
     * [splitIntoLessons] is how it becomes lessons and attendance.
     */
    val classes: List<LegacyClass> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
) {
    companion object {
        /**
         * 2 added groups; **3 gave a group its own hourly rate** (`student_groups.priceMinor`);
         * **4 gave a group its subject** (`student_groups.subjectId`); **5 carries the rate log**
         * (`price_history`); **6 splits a class into a lesson and an attendance** — the lesson's hour and
         * status once per lesson, who was in it and what they owe once per student; **7 carries a
         * lesson's note** (`lessons.notes`).
         *
         * Older files still import — a v1 has no groups at all, a v2's groups come back priced at 0
         * for the tutor to re-price, a v3's come back with no subject, a v4's arrive with no rate
         * history (an empty log), a v5's classes are split on the way in ([splitIntoLessons]), and a
         * v6's lessons come back with no note. A restore otherwise reproduces the file rather than
         * inventing rows it doesn't contain, so each of those gaps stays a gap.
         *
         * The bump is for the other direction: a v6 file *would* parse in a build that predates the
         * split — its `classes` list is empty, so it would restore a database with no lessons in it at
         * all — so that build refuses it instead (see [isSupported]). A v7 file in a v6 build is the
         * milder version of the same thing: the parser ignores unknown keys, so every note in it would
         * be dropped in silence. Dropping a tutor's own writing without saying so is exactly what a
         * version is for, and it is the reason each of 3, 4 and 5 bumped for a single column too.
         */
        const val CURRENT_VERSION = 7

        /**
         * Whether a backup of [version] can be safely imported by this build. A newer version may
         * carry data this build doesn't understand, so it's rejected rather than imported with
         * fields silently dropped (the parser ignores unknown keys).
         */
        fun isSupported(version: Int): Boolean = version in 1..CURRENT_VERSION
    }
}

/**
 * The v1–v5 classes of a file, as the lessons and attendance rows they describe.
 *
 * One lesson per `(groupId, startMillis)`, its status the strictest thing true of all its members
 * ([LessonStatus.PRECEDENCE]), and each member's attendance read off the pair of statuses.
 *
 * **This is the last reader of the pre-v2 shape.** It had a twin in SQL — `MIGRATION_1_2`, doing the same
 * thing to the table these rows were read from — and that went with the migration squash, because no
 * database is below the schema baseline any more (`AppDatabase.BASELINE_VERSION`). A backup **file** is
 * different: one written by an older build is a file on somebody's disk, and it still has to import.
 * `BackupSplitTest` pins what this produces.
 *
 * Ids are preserved: a lesson takes the lowest id of its classes, and every attendance row keeps its
 * class's id, so a charge, a calendar event and any id noted elsewhere all still point where they did.
 */
fun List<LegacyClass>.splitIntoLessons(): Pair<List<LessonEntity>, List<AttendanceEntity>> {
    // An individual class is its own lesson, so it is keyed by its own id; a group's members share theirs.
    val byLesson = groupBy { c -> c.groupId?.let { it to c.startMillis } ?: (null to c.id) }
    val lessons = byLesson.map { (_, members) ->
        val first = members.minBy { it.id }
        LessonEntity(
            id = first.id,
            subjectId = first.subjectId,
            startMillis = first.startMillis,
            durationMinutes = first.durationMinutes,
            status = lessonStatusOf(members).name,
            groupId = first.groupId,
            mode = first.mode,
        )
    }
    val attendance = byLesson.flatMap { (_, members) ->
        val lessonId = members.minOf { it.id }
        val lessonStatus = lessonStatusOf(members)
        members.map { c ->
            AttendanceEntity(
                id = c.id,
                lessonId = lessonId,
                studentId = c.studentId,
                state = attendedOf(c.status, lessonStatus).name,
                priceMinor = c.priceMinor,
                chargeMinor = c.chargeMinor,
                calendarEventId = c.calendarEventId,
            )
        }
    }
    return lessons.sortedBy { it.id } to attendance.sortedBy { it.id }
}

/**
 * The lesson's status: **scheduled if any member still is**, else completed if any is, else cancelled —
 * [LessonStatus.PRECEDENCE], which is where that order is stated and pinned. It used to be
 * `entries.firstOrNull`, i.e. the order the constants happen to be declared in.
 *
 * An unreadable status counts as none of the three, and is left to `BackupIntegrity` to refuse — this
 * only has to not crash on it.
 */
private fun lessonStatusOf(members: List<LegacyClass>): LessonStatus =
    LessonStatus.PRECEDENCE.firstOrNull { status -> members.any { it.status == status.name } }
        ?: LessonStatus.SCHEDULED

/**
 * A member's attendance, from their old status and their lesson's. Present if they were completed;
 * expected if they were scheduled — or if the lesson was called off, since nobody was absent from an hour
 * that did not happen; absent if their own class was cancelled inside a lesson that went ahead, which is
 * exactly the correction the old model could only express that way.
 */
private fun attendedOf(stored: String, lesson: LessonStatus): Attended = when {
    stored == LessonStatus.COMPLETED.name -> Attended.PRESENT
    stored == LessonStatus.SCHEDULED.name -> Attended.EXPECTED
    lesson == LessonStatus.CANCELLED -> Attended.EXPECTED
    else -> Attended.ABSENT
}

/**
 * The rows this file carries, as the database's own [DatabaseSnapshot].
 *
 * The two shapes hold the same lists and are still separate types, because they answer to different
 * masters: the field names here are **JSON keys in files already on somebody's phone**, and so may never
 * be renamed, while the snapshot follows the schema. Nesting one inside the other to save the mapping
 * would have made the file format a projection of the database, and the next table added would have
 * changed the JSON shape of every backup ever written. This is where that pays for itself: the schema
 * split one table into two, and the old files did not change at all.
 *
 * A version older than [BackupData.CURRENT_VERSION] simply arrives with its newer lists empty — a
 * restore reproduces the file rather than inventing rows it does not contain. The exception is
 * [BackupData.classes], which *is* the older shape of two of those lists and is converted on the way in.
 */
fun BackupData.toSnapshot(): DatabaseSnapshot {
    val (splitLessons, splitAttendance) = classes.splitIntoLessons()
    return DatabaseSnapshot(
        subjects = subjects,
        students = students,
        studentSubjects = studentSubjects,
        groups = groups,
        groupMembers = groupMembers,
        prices = prices,
        rateHistory = rateHistory,
        lessons = lessons + splitLessons,
        attendance = attendance + splitAttendance,
        payments = payments,
    )
}

/** The database's rows as a backup file of this build's version. */
fun DatabaseSnapshot.toBackupData(): BackupData = BackupData(
    subjects = subjects,
    students = students,
    studentSubjects = studentSubjects,
    groups = groups,
    groupMembers = groupMembers,
    prices = prices,
    rateHistory = rateHistory,
    lessons = lessons,
    attendance = attendance,
    payments = payments,
)
