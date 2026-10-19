package com.seblful.klas.store

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.ThemeChoice
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.minutes

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

    /** Who is in the tutor's book, by name: what the lessons, payments and homework are written against. */
    class Roster(
        private val subjectIds: Map<DemoSubject, Long>,
        private val studentIds: Map<String, Long>,
        val memberIds: Map<String, Long>,
        val groupId: Long,
    ) {
        fun subjectId(subject: DemoSubject): Long = subjectIds.getValue(subject)
        fun studentId(name: String): Long = studentIds.getValue(name)
    }

    /** One student's lessons or the group's, with the ids they are booked under. */
    private class Course(val weekly: Weekly, val subjectId: Long, val studentIds: List<Long>, val groupId: Long?) {
        val price: Money get() = Money.ofMajor(weekly.rate)

        /** Only one-to-one lessons are ever called off: a group's goes ahead with whoever comes. */
        val callsOff: Boolean get() = groupId == null
    }

    /** A taught lesson's charge to one student, which is what a payment settles. */
    private data class Charge(val studentId: Long, val day: LocalDate, val amount: Money)

    /** The tutor's half-year, written in; what the promo video then changes, through [Seeded]. */
    suspend fun seed(tutor: DemoTutor): Seeded {
        settings.update {
            it.copy(
                onboardingComplete = true,
                language = tutor.language,
                currency = tutor.currency,
                themeMode = ThemeChoice.LIGHT,
            )
        }
        val roster = enroll(tutor)
        pay(tutor, roster, teach(tutor.students.map { course(it, roster) } + course(tutor.group, roster)))
        setHomework(tutor, roster)
        return Seeded(tutor, roster)
    }

    /** The seeded book, for the changes the promo video shows made to it once the stills are shot. */
    inner class Seeded(private val tutor: DemoTutor, private val roster: Roster) {

        /** The homework the video types, set for the student whose lesson is on now ([DemoTutor.onNow]). */
        suspend fun giveTypedHomework() {
            homework.give(Assignee.Student(roster.studentId(tutor.onNow.name)), tutor.typedHomework)
        }

        /** Books [DemoTutor.UNMARKED] with [DemoTutor.freeToday]; returns whom it is with, which names its row. */
        suspend fun bookUnmarkedLesson(): String {
            val student = tutor.freeToday
            book(course(student, roster), DemoTutor.TODAY, DemoTutor.UNMARKED)
            return student.name
        }
    }

    private suspend fun enroll(tutor: DemoTutor): Roster {
        val subjectIds = DemoSubject.entries.associateWith { subjects.upsert(Subject(name = tutor.subjects[it])) }
        val studentIds = tutor.students.associate { s ->
            s.name to enrollment.createStudent(
                Student(name = s.name, phone = s.phone, email = s.email, defaultMode = s.mode),
                mapOf(subjectIds.getValue(s.subject) to Money.ofMajor(s.rate)),
            )
        }
        // Each member also takes the subject one to one, at what the tutor's other students of it pay.
        val subject = subjectIds.getValue(tutor.group.subject)
        val oneToOne = Money.ofMajor(tutor.students.first { it.subject == tutor.group.subject }.rate)
        val memberIds = tutor.group.members.associateWith { name ->
            enrollment.createStudent(Student(name = name), mapOf(subject to oneToOne))
        }
        val groupId = groups.save(
            Group(name = tutor.group.name, subjectId = subject, price = Money.ofMajor(tutor.group.rate)),
            memberIds.values.toSet(),
        ).id
        return Roster(subjectIds, studentIds, memberIds, groupId)
    }

    private fun course(student: DemoStudent, roster: Roster) =
        Course(student, roster.subjectId(student.subject), listOf(roster.studentId(student.name)), groupId = null)

    private fun course(group: DemoGroup, roster: Roster) =
        Course(group, roster.subjectId(group.subject), roster.memberIds.values.toList(), roster.groupId)

    /**
     * Every weekly slot of [courses] from [DemoTutor.FIRST_DAY] on, booked, and those over by
     * [DemoTutor.NOW] marked taught — now and then called off; returns the charges of those taught.
     */
    private suspend fun teach(courses: List<Course>): List<Charge> {
        val charges = mutableListOf<Charge>()
        var oneToOne = 0
        for (day in DemoTutor.FIRST_DAY..DemoTutor.BOOKED_UNTIL) {
            for (course in courses) {
                for (slot in course.weekly.slotsOn(day)) {
                    val cancel = course.callsOff && oneToOne++ % CANCEL_EVERY == CANCEL_FIRST
                    val ids = book(course, day, slot)
                    if (slot.endOn(day) > DemoTutor.CLOCK.now()) continue
                    val status = if (cancel) LessonStatus.CANCELLED else LessonStatus.COMPLETED
                    classes.setStatus(classes.getSessionFor(ids.first())!!, status)
                    if (status == LessonStatus.COMPLETED) {
                        val charge = ClassRules.chargeFor(course.price, slot.minutes)
                        course.studentIds.mapTo(charges) { Charge(it, day, charge) }
                    }
                }
            }
        }
        return charges
    }

    private suspend fun book(course: Course, day: LocalDate, slot: Slot): List<Long> = classes.book(
        subjectId = course.subjectId,
        start = slot.startOn(day),
        durationMinutes = slot.minutes,
        mode = course.weekly.mode,
        studentIds = course.studentIds,
        price = course.price,
        groupId = course.groupId,
    ).written()

    private suspend fun setHomework(tutor: DemoTutor, roster: Roster) {
        for (s in tutor.students) {
            s.homework?.let { homework.give(Assignee.Student(roster.studentId(s.name)), it) }
        }
        homework.give(Assignee.Group(roster.groupId), tutor.group.homework)
        for ((name, text) in tutor.doneHomework) {
            val given = homework.give(Assignee.Student(roster.studentId(name)), text)
            homework.setDone(given, done = true)
        }
    }

    /**
     * Most students pay at the end of each lesson. Two stopped a few weeks ago (the debts), one paid a
     * block of lessons ahead at the start of the month (the credit), and one member of the group has
     * missed the last fortnight.
     */
    private suspend fun pay(tutor: DemoTutor, roster: Roster, charges: List<Charge>) {
        val monthStart = LocalDate(DemoTutor.TODAY.year, DemoTutor.TODAY.month, 1)
        val stopsPaying = buildMap {
            tutor.students.forEach { s -> s.paysUntil?.let { put(roster.studentId(s.name), it) } }
            put(roster.memberIds.getValue(tutor.group.owing), DemoTutor.TODAY.minus(2, DateTimeUnit.WEEK))
        }
        val prepaid = tutor.students.filter { it.prepaysLessons > 0 }.associateBy { roster.studentId(it.name) }

        charges
            .filter { c -> stopsPaying[c.studentId]?.let { !(c.day > it) } ?: true }
            .filter { c -> c.studentId !in prepaid || c.day < monthStart }
            .groupBy { it.studentId to it.day }
            .forEach { (key, dayCharges) ->
                payments.record(key.first, Money(dayCharges.sumOf { it.amount.minor }), key.second)
            }
        prepaid.forEach { (id, s) ->
            payments.record(id, Money.ofMajor(s.rate * s.prepaysLessons), monthStart)
        }
    }

    private companion object {
        /** Roughly one lesson in seventeen is called off, as in any real half-year: the sixth, the 23rd, … */
        const val CANCEL_EVERY = 17
        const val CANCEL_FIRST = 5
    }
}
