package com.seblful.klas.testutil

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import java.time.Duration
import java.time.Instant

/**
 * Seeds a test's starting state in the vocabulary the app uses — a student, a lesson, a group with a
 * roster — by going through the **repositories**.
 *
 * Tests used to seed by writing entities straight to the fake DAOs. Two things followed from that.
 *
 * The first is that the invariant each repository exists to hold was skipped on the way in, so a test
 * could set up a state the app cannot reach and then assert something about it. The second is that
 * every test built the rows itself, and they disagreed. "A completed class" existed in three versions:
 * one set `chargeMinor` and left `priceMinor` at zero, one hard-coded `priceMinor = 20` regardless of
 * the charge it was passed, and one set `priceMinor` and omitted `chargeMinor` entirely. All three
 * passed, because each asserted only the field it had remembered to fill in.
 *
 * Here a completed lesson is booked and then completed, exactly as the app does it, so its charge is
 * whatever `ClassRules` says it is rather than whatever the test thought to type.
 *
 * Reach for [TestEnv]'s DAOs directly only to construct something the repositories deliberately
 * refuse — a malformed row on a read path, say. That is a real need, and it should look unusual.
 */
class Fixtures(private val env: TestEnv) {

    /** A student, with an optional email so the calendar's guest handling can be driven. */
    suspend fun student(name: String = "Аня", email: String = ""): Long =
        env.studentDao.upsert(StudentEntity(name = name, email = email))

    /**
     * A student who has left — archived rather than deleted, which is how somebody stops appearing in
     * the pickers without their history going with them. Archiving goes through the repository, so any
     * lesson they still had scheduled is cancelled exactly as it would be in the app.
     */
    suspend fun archivedStudent(name: String = "Аня", email: String = ""): Long =
        student(name, email).also { id ->
            env.studentRepo.setArchived(env.studentRepo.getById(id)!!, archived = true)
        }

    suspend fun subject(name: String = "Алгебра"): Long =
        env.subjectDao.upsert(SubjectEntity(name = name))

    /**
     * A student priced for a subject — which is the only kind that can be booked for it, since a
     * student's subjects *are* their priced subjects.
     */
    suspend fun pricedStudent(
        name: String = "Аня",
        subjectId: Long,
        rate: Money = Money.ofMajor(30),
        email: String = "",
    ): Long = student(name, email).also { env.enrollmentRepo.enroll(it, subjectId, rate) }

    /** A group taught [subjectId], with [members] on its roster and its own hourly rate. */
    suspend fun group(
        name: String = "11 «А»",
        subjectId: Long? = null,
        members: Set<Long> = emptySet(),
        rate: Money = Money.ofMajor(20),
    ): Long = env.groupRepo.save(Group(name = name, subjectId = subjectId, price = rate), members).id

    /**
     * One booked lesson — a class per member, all at [rate], as `ClassRepository.book` writes it.
     * Returns the new class ids, in the order the members were given.
     */
    suspend fun lesson(
        subjectId: Long,
        students: List<Long>,
        start: Instant,
        minutes: Int = 60,
        rate: Money = Money.ofMajor(30),
        groupId: Long? = null,
        mode: ClassMode = ClassMode.IN_PERSON,
    ): List<Long> = env.classRepo.book(
        subjectId = subjectId,
        start = start,
        durationMinutes = minutes,
        mode = mode,
        studentIds = students,
        price = rate,
        groupId = groupId,
    ).written()

    /**
     * A lesson that has been taught: booked, then completed through the same status change the tutor
     * makes, so its charge is `rate × duration` by the production rule rather than by the test's
     * arithmetic.
     */
    suspend fun completedLesson(
        subjectId: Long,
        students: List<Long>,
        start: Instant,
        minutes: Int = 60,
        rate: Money = Money.ofMajor(30),
        groupId: Long? = null,
    ): List<Long> = lesson(subjectId, students, start, minutes, rate, groupId)
        .also { ids -> setStatus(ids.first(), LessonStatus.COMPLETED) }

    /** A lesson called off, so the hour is free again and nothing is billed. */
    suspend fun cancelledLesson(
        subjectId: Long,
        students: List<Long>,
        start: Instant,
        minutes: Int = 60,
        rate: Money = Money.ofMajor(30),
        groupId: Long? = null,
    ): List<Long> = lesson(subjectId, students, start, minutes, rate, groupId)
        .also { ids -> setStatus(ids.first(), LessonStatus.CANCELLED) }

    /** Moves the whole lesson class [id] belongs to into [status]. */
    suspend fun setStatus(id: Long, status: LessonStatus) {
        val session = env.classRepo.getSessionFor(id) ?: error("No lesson for class $id")
        env.classRepo.setStatus(session, status)
    }

    /**
     * Records how one student's class went — the per-member correction that used to be a status change
     * to a session of one. What "the absentee of a lesson that went ahead" is built with.
     */
    suspend fun setAttendance(id: Long, attended: Attended) {
        val klass = env.classRepo.getById(id) ?: error("No class $id")
        env.classRepo.setAttendance(klass, attended)
    }

    /**
     * Writes a lesson and its students **straight to the tables**, bypassing every rule — for the state
     * the repositories refuse to produce: a malformed row on a read path, a charge that does not match
     * its status, a lesson in the past still marked scheduled.
     *
     * Takes the flat shape a class used to be stored in, which is what such a test is describing: one
     * student, one hour, one status. It is here rather than in each test so that the two rows go in
     * together and point at each other — a lesson with no attendance is not a class, and an attendance
     * row with no lesson is invisible to every read.
     *
     * Returns the new class (attendance) id.
     */
    suspend fun storedClass(
        studentId: Long,
        subjectId: Long,
        startMillis: Long,
        durationMinutes: Int = 60,
        status: String = LessonStatus.SCHEDULED.name,
        state: String? = null,
        priceMinor: Long = 0,
        chargeMinor: Long = 0,
        groupId: Long? = null,
        mode: String = ClassMode.IN_PERSON.name,
        calendarEventId: String? = null,
        lessonId: Long = 0,
    ): Long {
        // Which rows are one lesson, the flat shape's own way: a group's members share a `(groupId,
        // startMillis)` pair. That was the *only* answer before the lesson had a row, so a call describing
        // a second member of an already-seeded group lesson means to join it rather than to hold the same
        // hour twice. An individual class is always its own lesson.
        val existing = lessonId.takeIf { it != 0L }
            ?: groupId?.let { g ->
                env.lessonDao.items.value
                    .firstOrNull { it.groupId == g && it.startMillis == startMillis }?.id
            }
        val lesson = env.lessonDao.upsert(
            LessonEntity(
                id = existing ?: 0,
                subjectId = subjectId,
                startMillis = startMillis,
                durationMinutes = durationMinutes,
                status = status,
                groupId = groupId,
                mode = mode,
            ),
        )
        return env.attendanceDao.upsert(
            AttendanceEntity(
                lessonId = lesson,
                studentId = studentId,
                // The attendance a status implies, so a test that only cares about the status does not
                // have to know about attendance at all — and the pair still bills what it should.
                state = state ?: if (status == LessonStatus.COMPLETED.name) {
                    Attended.PRESENT.name
                } else {
                    Attended.EXPECTED.name
                },
                priceMinor = priceMinor,
                chargeMinor = chargeMinor,
                calendarEventId = calendarEventId,
            ),
        )
    }

    /**
     * Homework for [assignee], given at [at] — through the repository, which stamps it with the clock, so
     * the clock is moved to [at] for the write and put back after. [done] marks it done at the same moment.
     */
    suspend fun homework(
        assignee: Assignee,
        text: String = "§ 12, упр. 4–7",
        at: Instant = env.clock.instant(),
        done: Boolean = false,
    ): Homework {
        val now = env.clock.instant()
        env.clock.advanceBy(Duration.between(now, at).toMillis())
        val given = env.homeworkRepo.give(assignee, text)
        if (done) env.homeworkRepo.setDone(given, done = true)
        env.clock.advanceBy(Duration.between(at, now).toMillis())
        return if (done) given.copy(doneAt = at) else given
    }

    /** Calendar mirroring on, which it is not by default. */
    suspend fun withCalendarSync(invitesEnabled: Boolean = false) {
        env.settingsRepo.update {
            it.copy(calendarSyncEnabled = true, emailInvitesEnabled = invitesEnabled)
        }
    }
}
