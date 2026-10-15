package com.seblful.klas.store

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.ThemeChoice
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Writes [DemoTutor] into the app's database **through the repositories**, the way the tutor would have
 * typed it: a lesson is booked, then marked taught, so every charge, balance and report total on the
 * screenshots is the production rule's answer rather than a number put there for the picture.
 */
internal class DemoSeeder @Inject constructor(
    private val settings: SettingsRepository,
    private val subjects: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    private val groups: GroupRepository,
    private val classes: ClassRepository,
    private val payments: PaymentRepository,
    private val homework: HomeworkRepository,
) {

    private val now: Instant = DemoTutor.NOW.atZone(DemoTutor.ZONE).toInstant()
    private lateinit var roster: Roster

    /** A taught lesson's charge to one student, which is what a payment settles. */
    private data class Charge(val studentId: Long, val day: LocalDate, val amount: Money)

    /** Who is in the tutor's book, by name: what the lessons, payments and homework are written against. */
    private data class Roster(
        val subjectIds: Map<Subject, Long>,
        val studentIds: Map<String, Long>,
        val memberIds: Map<String, Long>,
        val groupId: Long,
    ) {
        val math: Long get() = subjectIds.getValue(Subject.MATH)
    }

    suspend fun seed(tutor: DemoTutor) {
        settings.update {
            it.copy(
                onboardingComplete = true,
                language = tutor.language,
                currency = tutor.currency,
                themeMode = ThemeChoice.LIGHT,
            )
        }
        roster = enroll(tutor)
        pay(tutor, teach(tutor, roster), roster)
        setHomework(tutor, roster)
    }

    private suspend fun enroll(tutor: DemoTutor): Roster {
        val subjectIds = mapOf(
            Subject.MATH to tutor.subjects.math,
            Subject.PHYSICS to tutor.subjects.physics,
            Subject.LANGUAGE to tutor.subjects.language,
        ).mapValues { (_, name) -> subjects.upsert(com.seblful.klas.domain.model.Subject(name = name)) }
        val math = subjectIds.getValue(Subject.MATH)

        val studentIds = tutor.students.associate { s ->
            s.name to enrollment.createStudent(
                Student(name = s.name, phone = s.phone, email = s.email, defaultMode = s.mode),
                mapOf(subjectIds.getValue(s.subject) to Money.ofMajor(s.rate)),
            )
        }
        val individualRate = tutor.students.first { it.subject == Subject.MATH }.rate
        val memberIds = tutor.group.members.associateWith { name ->
            enrollment.createStudent(Student(name = name), mapOf(math to Money.ofMajor(individualRate)))
        }
        val groupId = groups.save(
            Group(name = tutor.group.name, subjectId = math, price = Money.ofMajor(tutor.group.rate)),
            memberIds.values.toSet(),
        ).id
        return Roster(subjectIds, studentIds, memberIds, groupId)
    }

    /** Every weekly slot from [DemoTutor.FIRST_DAY] on, booked; returns the charges of those taught. */
    private suspend fun teach(tutor: DemoTutor, roster: Roster): List<Charge> {
        val charges = mutableListOf<Charge>()
        var booked = 0
        var day = DemoTutor.FIRST_DAY
        while (!day.isAfter(DemoTutor.BOOKED_UNTIL)) {
            for (s in tutor.students) {
                for (slot in s.slots.filter { it.day == day.dayOfWeek && !day.isBefore(s.from) }) {
                    val id = roster.studentIds.getValue(s.name)
                    val taught = lesson(
                        subjectId = roster.subjectIds.getValue(s.subject), day = day, slot = slot, mode = s.mode,
                        students = listOf(id), rate = s.rate, groupId = null, cancel = booked++ % CANCEL_EVERY == 5,
                    )
                    if (taught) charges += Charge(id, day, perStudent(s.rate, slot.minutes))
                }
            }
            for (slot in tutor.group.slots.filter { it.day == day.dayOfWeek && !day.isBefore(tutor.group.from) }) {
                val members = roster.memberIds.values.toList()
                val taught = lesson(
                    subjectId = roster.math, day = day, slot = slot, mode = ClassMode.IN_PERSON,
                    students = members, rate = tutor.group.rate, groupId = roster.groupId, cancel = false,
                )
                if (taught) members.forEach { charges += Charge(it, day, perStudent(tutor.group.rate, slot.minutes)) }
            }
            day = day.plusDays(1)
        }
        return charges
    }

    /** The homework the promo video types, set for the student whose lesson is on now ([DemoTutor.onNow]). */
    suspend fun giveTypedHomework(tutor: DemoTutor) {
        homework.give(Assignee.Student(roster.studentIds.getValue(tutor.onNow.name)), tutor.typedHomework)
    }

    private suspend fun setHomework(tutor: DemoTutor, roster: Roster) {
        for (s in tutor.students) {
            s.homework?.let { homework.give(Assignee.Student(roster.studentIds.getValue(s.name)), it) }
        }
        homework.give(Assignee.Group(roster.groupId), tutor.group.homework)
        for ((name, text) in tutor.doneHomework) {
            val given = homework.give(Assignee.Student(roster.studentIds.getValue(name)), text)
            homework.setDone(given, done = true)
        }
    }

    /**
     * Books one lesson and, if it is over by [DemoTutor.NOW], marks it taught (or, now and then, called
     * off). Returns whether it was taught — what a payment then settles.
     */
    private suspend fun lesson(
        subjectId: Long,
        day: LocalDate,
        slot: Slot,
        mode: ClassMode,
        students: List<Long>,
        rate: Long,
        groupId: Long?,
        cancel: Boolean,
    ): Boolean {
        val start = day.atTime(slot.time).atZone(DemoTutor.ZONE).toInstant()
        val ids = classes.book(
            subjectId = subjectId,
            start = start,
            durationMinutes = slot.minutes,
            mode = mode,
            studentIds = students,
            price = Money.ofMajor(rate),
            groupId = groupId,
        ).written()
        val over = !start.plusSeconds(slot.minutes * 60L).isAfter(now)
        if (!over) return false
        val status = if (cancel) LessonStatus.CANCELLED else LessonStatus.COMPLETED
        classes.setStatus(classes.getSessionFor(ids.first())!!, status)
        return status == LessonStatus.COMPLETED
    }

    /**
     * Most students pay at the end of each lesson. Two stopped a few weeks ago (the debts), one paid a
     * block of lessons ahead at the start of the month (the credit), and one member of the group has
     * missed the last fortnight.
     */
    private suspend fun pay(tutor: DemoTutor, charges: List<Charge>, roster: Roster) {
        val studentIds = roster.studentIds
        val today = DemoTutor.NOW.toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val stopsPaying = buildMap {
            tutor.students.forEach { s -> s.paysUntil?.let { put(studentIds.getValue(s.name), it) } }
            put(roster.memberIds.getValue(tutor.group.owing), today.minusWeeks(2))
        }
        val prepaid = tutor.students.filter { it.prepaysLessons > 0 }
            .associate { studentIds.getValue(it.name) to it }

        charges
            .filter { c -> stopsPaying[c.studentId]?.let { !c.day.isAfter(it) } ?: true }
            .filter { c -> c.studentId !in prepaid || c.day.isBefore(monthStart) }
            .groupBy { it.studentId to it.day }
            .forEach { (key, dayCharges) ->
                payments.record(key.first, Money(dayCharges.sumOf { it.amount.minor }), key.second)
            }
        prepaid.forEach { (id, s) ->
            payments.record(id, Money.ofMajor(s.rate * s.prepaysLessons), monthStart)
        }
    }

    private fun perStudent(rate: Long, minutes: Int) = Money(rate * 100 * minutes / 60)

    private companion object {
        /** Roughly one lesson in seventeen is called off, as in any real half-year. */
        const val CANCEL_EVERY = 17
    }
}
