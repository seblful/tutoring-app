package com.seblful.klas.data.backup

import com.seblful.klas.data.billing.PaymentEntity
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.homework.HomeworkEntity
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.GroupMemberCrossRef
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

/**
 * One class as versions 1–5 wrote it: a row per student carrying the lesson's own facts alongside their
 * own. **Frozen.** It is the shape of files already on somebody's phone, so it can neither change nor go
 * away while those files are still meant to import — see [BackupData.classes] and [splitIntoLessons].
 *
 * [status] and [mode] are typed as the entities' are, which reads the same JSON: a name in, the constant
 * out, and a name this build has no constant for refused as the file is parsed (see [BackupManager.parse]).
 */
@Serializable
data class LegacyClass(
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val priceMinor: Long = 0,
    val status: LessonStatus,
    val chargeMinor: Long = 0,
    val groupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
    val calendarEventId: String? = null,
)

/**
 * A lesson as a file of **v6 and later** carries it: [LessonEntity]'s columns, under the same keys — plus
 * [homework], which only a v8 file holds and the table no longer has.
 *
 * A type of its own rather than the entity, which `lessons` was until v10: the entity follows the schema,
 * and v10 dropped `lessons.homework` from it, while a v8 file on somebody's disk still holds the text and
 * has to restore it ([legacyHomework]). The note a v7 or v8 file holds (`notes`) is not declared at all —
 * it is not restored, and the parser ignores a key it does not know.
 *
 * @param homework what a v8 lesson set. **Read, never written**: blank in everything this build exports,
 *   and `NEVER` leaves the key out of the file rather than writing it empty.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupLesson(
    val id: Long = 0,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val status: LessonStatus,
    val groupId: Long? = null,
    val mode: ClassMode = ClassMode.IN_PERSON,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val homework: String = "",
)

/**
 * A student's place in a lesson as a file of **v6 and later** carries it: [AttendanceEntity]'s columns,
 * plus [homeworkResult] — a v8 file's verdict, for the reason [BackupLesson] gives.
 *
 * @param homeworkResult how the student did v8's homework. **Read, never written.**
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupAttendance(
    val id: Long = 0,
    val lessonId: Long,
    val studentId: Long,
    val state: Attended,
    val priceMinor: Long = 0,
    val chargeMinor: Long = 0,
    val calendarEventId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val homeworkResult: String? = null,
)

/** The row as the table holds it — whatever a v8 file carried beside it is [legacyHomework]'s. */
fun BackupLesson.toEntity() = LessonEntity(
    id = id,
    subjectId = subjectId,
    startMillis = startMillis,
    durationMinutes = durationMinutes,
    status = status,
    groupId = groupId,
    mode = mode,
)

fun BackupAttendance.toEntity() = AttendanceEntity(
    id = id,
    lessonId = lessonId,
    studentId = studentId,
    state = state,
    priceMinor = priceMinor,
    chargeMinor = chargeMinor,
    calendarEventId = calendarEventId,
)

fun LessonEntity.toBackup() = BackupLesson(
    id = id,
    subjectId = subjectId,
    startMillis = startMillis,
    durationMinutes = durationMinutes,
    status = status,
    groupId = groupId,
    mode = mode,
)

fun AttendanceEntity.toBackup() = BackupAttendance(
    id = id,
    lessonId = lessonId,
    studentId = studentId,
    state = state,
    priceMinor = priceMinor,
    chargeMinor = chargeMinor,
    calendarEventId = calendarEventId,
)

/**
 * A full snapshot of the database, serialized to JSON for backup/restore. Stores the raw
 * persisted rows verbatim (ids included) so importing into an empty database reproduces the
 * exact same data. [version] guards the format for future migrations.
 */
@Serializable
data class BackupData(
    // Every supported export has this envelope, even when empty. Defaults remain useful to callers,
    // but JSON must name these fields so an unrelated object cannot become an empty replacement.
    @Required val version: Int = CURRENT_VERSION,
    @Required val subjects: List<SubjectEntity> = emptyList(),
    @Required val students: List<StudentEntity> = emptyList(),
    val studentSubjects: List<StudentSubjectCrossRef> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val groupMembers: List<GroupMemberCrossRef> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    /** The rate log (`price_history`) — the record of what prices used to be. */
    val rateHistory: List<RateChangeEntity> = emptyList(),
    /**
     * The lessons — **v6 and later**. Empty in an older file, whose lessons are inside [classes] instead.
     */
    val lessons: List<BackupLesson> = emptyList(),
    /** Who was in each lesson, and what it cost them — **v6 and later**. */
    val attendance: List<BackupAttendance> = emptyList(),
    /**
     * The flat classes of **v1–v5**, and empty in anything this build writes. Kept because a backup file
     * is the only way a tutor's data crosses builds: an old file still has to restore, and
     * [splitIntoLessons] is how it becomes lessons and attendance.
     */
    val classes: List<LegacyClass> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    /**
     * Homework — **v9 and later**. A v8 file keeps its homework on [lessons] and [attendance] instead,
     * and [legacyHomework] is how it becomes rows of this.
     */
    val homework: List<HomeworkEntity> = emptyList(),
) {
    companion object {
        /**
         * Bump when an older build could silently discard data in a newer file. Versions before 6 store
         * flat classes and are converted by [splitIntoLessons]; v8 homework is converted by
         * [legacyHomework]. Older lesson notes are not restored. Schema v10 removed only fields that were
         * already blank, so it did not require a backup version bump.
         */
        const val CURRENT_VERSION = 9

        /**
         * Whether a backup of [version] can be safely imported by this build. A newer version may
         * carry data this build doesn't understand, so it's rejected rather than imported with
         * fields silently dropped (the parser ignores unknown keys).
         */
        fun isSupported(version: Int): Boolean = version in 1..CURRENT_VERSION
    }
}

/**
 * Converts backup versions 1–5 from flat classes into lessons and attendance. Rows are grouped by
 * `(groupId, startMillis)`; IDs are preserved, and [LessonStatus.PRECEDENCE] resolves group status.
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
            status = lessonStatusOf(members),
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
                state = attendedOf(c.status, lessonStatus),
                priceMinor = c.priceMinor,
                chargeMinor = c.chargeMinor,
                calendarEventId = c.calendarEventId,
            )
        }
    }
    return lessons.sortedBy { it.id } to attendance.sortedBy { it.id }
}

private fun lessonStatusOf(members: List<LegacyClass>): LessonStatus =
    LessonStatus.PRECEDENCE.first { status -> members.any { it.status == status } }

private fun attendedOf(own: LessonStatus, lesson: LessonStatus): Attended = when {
    own == LessonStatus.COMPLETED -> Attended.PRESENT
    own == LessonStatus.SCHEDULED -> Attended.EXPECTED
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
        // A v8 file's homework is read into its own rows below, and leaves the lesson as the table holds
        // it; an older file's note is not the app's any more and is never read at all.
        lessons = lessons.map { it.toEntity() } + splitLessons,
        // Calendar ids belong to the exporting device, not to the lesson. Never let a file choose
        // which of this device's appointments the mirror may update or delete.
        attendance = (attendance.map { it.toEntity() } + splitAttendance).map { it.copy(calendarEventId = null) },
        payments = payments,
        // The v1-v5 classes carry no homework, so only the file's own lessons are looked at.
        homework = homework + legacyHomework(lessons, attendance),
    )
}

/** Converts v8 lesson homework into rows, excluding cancelled lessons. Group work is assigned to the
 * group; individual work is assigned to each attendee. A row is done when it has a verdict, or when all
 * group attendees have verdicts.
 */
fun legacyHomework(lessons: List<BackupLesson>, attendance: List<BackupAttendance>): List<HomeworkEntity> {
    val members = attendance.groupBy { it.lessonId }
    return lessons
        .filter { it.homework.isNotEmpty() && it.status != LessonStatus.CANCELLED }
        .sortedBy { it.startMillis }
        .flatMap { lesson ->
            val rows = members[lesson.id].orEmpty()
            if (lesson.groupId != null) {
                listOf(
                    HomeworkEntity(
                        groupId = lesson.groupId,
                        text = lesson.homework,
                        givenAtMillis = lesson.startMillis,
                        doneAtMillis = lesson.startMillis.takeIf { rows.all { it.homeworkResult != null } },
                    ),
                )
            } else {
                rows.map { row ->
                    HomeworkEntity(
                        studentId = row.studentId,
                        text = lesson.homework,
                        givenAtMillis = lesson.startMillis,
                        doneAtMillis = lesson.startMillis.takeIf { row.homeworkResult != null },
                    )
                }
            }
        }
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
    lessons = lessons.map { it.toBackup() },
    attendance = attendance.map { it.toBackup() },
    payments = payments,
    homework = homework,
)
