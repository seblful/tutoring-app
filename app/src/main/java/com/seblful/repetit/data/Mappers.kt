package com.seblful.repetit.data

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.PaymentEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.Price
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
