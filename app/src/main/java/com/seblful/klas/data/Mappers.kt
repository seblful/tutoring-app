package com.seblful.klas.data

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.ClassRow
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

/**
 * How the two text-stored enums come back, and what happens when the text is not one of their names.
 *
 * A column holding an enum's `name` is a column that can hold something else — a hand-edited database
 * is the only route left now that the backup import checks its rows (`BackupIntegrity`), but the read
 * path still has to answer. It runs inside the `Flow`s every screen collects, so *throwing* here does
 * not report an error to anybody: it kills the collector, and the screen simply never draws.
 *
 * So the two answers differ, by whether the column carries money:
 *
 * - **A mode does not.** It is a label and the calendar event's location, so an unreadable one falls
 *   back to [ClassMode.IN_PERSON] and the app carries on. One lesson reads «Очно» when it was remote,
 *   which is a wrong word on a screen rather than a screen that will not open.
 * - **A status does**, and so does an attendance: together they are what makes a lesson count as income
 *   (`TutoringClass.isBillable`), so guessing would move money — `SCHEDULED` un-bills a lesson that
 *   earned, `COMPLETED` invents one that did not, `ABSENT` writes off what a student owes. There is no
 *   safe fallback, so these fail — loudly and namedly, which is the best available outcome for a row that
 *   should be unreachable.
 */
private fun modeOf(stored: String): ClassMode = ClassMode.parse(stored) ?: ClassMode.IN_PERSON

private fun statusOf(stored: String, classId: Long): LessonStatus =
    LessonStatus.parse(stored) ?: error("Class $classId has an unreadable lesson status \"$stored\"")

private fun attendedOf(stored: String, classId: Long): Attended =
    Attended.parse(stored) ?: error("Class $classId has an unreadable attendance \"$stored\"")

fun SubjectEntity.toDomain() = Subject(id = id, name = name)
fun Subject.toEntity() = SubjectEntity(id = id, name = name)

fun StudentEntity.toDomain() =
    Student(id = id, name = name, phone = phone, email = email, note = note, defaultMode = modeOf(defaultMode), archived = archived)
fun Student.toEntity() =
    StudentEntity(id = id, name = name, phone = phone, email = email, note = note, defaultMode = defaultMode.name, archived = archived)

fun GroupEntity.toDomain() =
    Group(id = id, name = name, note = note, price = Money(priceMinor), subjectId = subjectId, defaultMode = modeOf(defaultMode), archived = archived)
fun Group.toEntity() =
    GroupEntity(id = id, name = name, note = note, priceMinor = price.minor, subjectId = subjectId, defaultMode = defaultMode.name, archived = archived)

fun PriceEntity.toDomain() = Price(studentId = studentId, subjectId = subjectId, amount = Money(amountMinor))
fun Price.toEntity() = PriceEntity(studentId = studentId, subjectId = subjectId, amountMinor = amount.minor)

/**
 * The one place `price_history`'s nullable owner columns are turned back into a [RateOwner].
 *
 * A row is either about a (student, subject) price or a group's rate; a row that is about neither
 * cannot be produced by [RateChange.toEntity] and could only come from a hand-edited database or a
 * backup file, so it fails loudly here instead of being silently attributed to the wrong owner.
 */
fun RateChangeEntity.toDomain() = RateChange(
    id = id,
    owner = when {
        studentId != null && subjectId != null -> RateOwner.StudentSubject(studentId, subjectId)
        groupId != null -> RateOwner.GroupRate(groupId)
        else -> error("Rate change $id belongs to neither a student's subject nor a group")
    },
    from = fromMinor?.let { Money(it) },
    to = Money(toMinor),
    at = atMillis?.let { Instant.ofEpochMilli(it) },
)

fun RateChange.toEntity() = RateChangeEntity(
    id = id,
    studentId = (owner as? RateOwner.StudentSubject)?.studentId,
    subjectId = (owner as? RateOwner.StudentSubject)?.subjectId,
    groupId = (owner as? RateOwner.GroupRate)?.groupId,
    fromMinor = from?.minor,
    toMinor = to.minor,
    atMillis = at?.toEpochMilli(),
)

fun PaymentEntity.toDomain() =
    Payment(id = id, studentId = studentId, date = Instant.ofEpochMilli(dateMillis), amount = Money(amountMinor))

fun Payment.toEntity() =
    PaymentEntity(id = id, studentId = studentId, dateMillis = date.toEpochMilli(), amountMinor = amount.minor)

fun ClassRow.toDomain() = TutoringClass(
    id = id,
    lessonId = lessonId,
    studentId = studentId,
    subjectId = subjectId,
    start = Instant.ofEpochMilli(startMillis),
    durationMinutes = durationMinutes,
    price = Money(priceMinor),
    status = statusOf(status, id),
    attended = attendedOf(state, id),
    charge = Money(chargeMinor),
    groupId = groupId,
    mode = modeOf(mode),
    calendarEventId = calendarEventId,
)

/**
 * The two rows a class is stored as. Which of them a write is allowed to touch is the model's whole
 * point, so the two directions are separate functions rather than one `toEntity`:
 *
 * - [toLessonEntity] is the **lesson's** facts, written once however many students are in it. Writing it
 *   per class would put the app back where it was, with *n* copies of one status.
 * - [toAttendanceEntity] is this student's own: whether they were there, at what rate, for what charge.
 */
fun TutoringClass.toLessonEntity() = LessonEntity(
    id = lessonId,
    subjectId = subjectId,
    startMillis = start.toEpochMilli(),
    durationMinutes = durationMinutes,
    status = status.name,
    groupId = groupId,
    mode = mode.name,
)

fun TutoringClass.toAttendanceEntity() = AttendanceEntity(
    id = id,
    lessonId = lessonId,
    studentId = studentId,
    state = attended.name,
    priceMinor = price.minor,
    chargeMinor = charge.minor,
    calendarEventId = calendarEventId,
)
