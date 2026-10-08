package com.seblful.klas.data

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.ClassRow
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import java.time.Instant

// The enum columns arrive typed: reading a stored name, and what an unreadable one becomes, is
// `data/local/EnumConverters`'s, once, rather than each mapper's.

fun SubjectEntity.toDomain() = Subject(id = id, name = name)
fun Subject.toEntity() = SubjectEntity(id = id, name = name)

fun StudentEntity.toDomain() =
    Student(id = id, name = name, phone = phone, email = email, note = note, defaultMode = defaultMode, archived = archived)
fun Student.toEntity() =
    StudentEntity(id = id, name = name, phone = phone, email = email, note = note, defaultMode = defaultMode, archived = archived)

fun GroupEntity.toDomain() =
    Group(id = id, name = name, note = note, price = Money(priceMinor), subjectId = subjectId, defaultMode = defaultMode, archived = archived)
fun Group.toEntity() =
    GroupEntity(id = id, name = name, note = note, priceMinor = price.minor, subjectId = subjectId, defaultMode = defaultMode, archived = archived)

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
    status = status,
    attended = state,
    charge = Money(chargeMinor),
    groupId = groupId,
    mode = mode,
    calendarEventId = calendarEventId,
)

/**
 * The two rows a class is stored as. Which of them a write is allowed to touch is the model's whole
 * point, so the two directions are separate functions rather than one `toEntity`:
 *
 * - [toLessonEntity] is the **lesson's** facts, written once however many students are in it. Writing it
 *   per class would put the app back where it was, with *n* copies of one status.
 * - [toAttendanceEntity] is this student's own: whether they were there, at what rate, for what charge.
 *
 * Neither writes the vestigial columns (`LessonEntity.notes`, `LessonEntity.homework`,
 * `AttendanceEntity.homeworkResult`), which keep their empty defaults.
 */
fun TutoringClass.toLessonEntity() = LessonEntity(
    id = lessonId,
    subjectId = subjectId,
    startMillis = start.toEpochMilli(),
    durationMinutes = durationMinutes,
    status = status,
    groupId = groupId,
    mode = mode,
)

fun TutoringClass.toAttendanceEntity() = AttendanceEntity(
    id = id,
    lessonId = lessonId,
    studentId = studentId,
    state = attended,
    priceMinor = price.minor,
    chargeMinor = charge.minor,
    calendarEventId = calendarEventId,
)

/**
 * The one place `homework`'s nullable owner columns are read back into an [Assignee] — as
 * [RateChangeEntity.toDomain] does for a rate, and failing as loudly for a row that is neither's.
 */
fun HomeworkEntity.toDomain() = Homework(
    id = id,
    assignee = when {
        studentId != null -> Assignee.Student(studentId)
        groupId != null -> Assignee.Group(groupId)
        else -> error("Homework $id is for neither a student nor a group")
    },
    text = text,
    givenAt = Instant.ofEpochMilli(givenAtMillis),
    doneAt = doneAtMillis?.let { Instant.ofEpochMilli(it) },
)

fun Homework.toEntity() = HomeworkEntity(
    id = id,
    studentId = (assignee as? Assignee.Student)?.studentId,
    groupId = (assignee as? Assignee.Group)?.groupId,
    text = text,
    givenAtMillis = givenAt.toEpochMilli(),
    doneAtMillis = doneAt?.toEpochMilli(),
)
