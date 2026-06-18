package com.seblful.tutoringapp.data

import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.PaymentEntity
import com.seblful.tutoringapp.data.local.PriceEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.Price
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.domain.model.TutoringClass
import java.time.Instant

fun SubjectEntity.toDomain() = Subject(id = id, name = name)
fun Subject.toEntity() = SubjectEntity(id = id, name = name)

fun StudentEntity.toDomain() =
    Student(id = id, name = name, phone = phone, note = note, defaultMode = ClassMode.valueOf(defaultMode), archived = archived)
fun Student.toEntity() =
    StudentEntity(id = id, name = name, phone = phone, note = note, defaultMode = defaultMode.name, archived = archived)

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
    status = ClassStatus.valueOf(status),
    charge = Money(chargeMinor),
    recurringGroupId = recurringGroupId,
    mode = ClassMode.valueOf(mode),
    calendarEventId = calendarEventId,
)

fun TutoringClass.toEntity() = ClassEntity(
    id = id,
    studentId = studentId,
    subjectId = subjectId,
    startMillis = start.toEpochMilli(),
    durationMinutes = durationMinutes,
    status = status.name,
    chargeMinor = charge.minor,
    recurringGroupId = recurringGroupId,
    mode = mode.name,
    calendarEventId = calendarEventId,
)
