package com.seblful.repetit.data

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.PaymentEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.RateChangeEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.Price
import com.seblful.repetit.domain.model.RateChange
import com.seblful.repetit.domain.model.RateOwner
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import java.time.Instant

fun SubjectEntity.toDomain() = Subject(id = id, name = name)
fun Subject.toEntity() = SubjectEntity(id = id, name = name)

fun StudentEntity.toDomain() =
    Student(id = id, name = name, phone = phone, email = email, note = note, defaultMode = ClassMode.valueOf(defaultMode), archived = archived)
fun Student.toEntity() =
    StudentEntity(id = id, name = name, phone = phone, email = email, note = note, defaultMode = defaultMode.name, archived = archived)

fun GroupEntity.toDomain() =
    Group(id = id, name = name, note = note, price = Money(priceMinor), subjectId = subjectId, defaultMode = ClassMode.valueOf(defaultMode), archived = archived)
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

fun ClassEntity.toDomain() = TutoringClass(
    id = id,
    studentId = studentId,
    subjectId = subjectId,
    start = Instant.ofEpochMilli(startMillis),
    durationMinutes = durationMinutes,
    price = Money(priceMinor),
    status = ClassStatus.valueOf(status),
    charge = Money(chargeMinor),
    groupId = groupId,
    mode = ClassMode.valueOf(mode),
    calendarEventId = calendarEventId,
)

fun TutoringClass.toEntity() = ClassEntity(
    id = id,
    studentId = studentId,
    subjectId = subjectId,
    startMillis = start.toEpochMilli(),
    durationMinutes = durationMinutes,
    priceMinor = price.minor,
    status = status.name,
    chargeMinor = charge.minor,
    groupId = groupId,
    mode = mode.name,
    calendarEventId = calendarEventId,
)
