package com.seblful.klas.testutil

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
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
    ): Long = env.groupRepo.save(Group(name = name, subjectId = subjectId, price = rate), members)

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
    )

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
        .also { ids -> setStatus(ids.first(), ClassStatus.COMPLETED) }

    /** A lesson called off, so the hour is free again and nothing is billed. */
    suspend fun cancelledLesson(
        subjectId: Long,
        students: List<Long>,
        start: Instant,
        minutes: Int = 60,
        rate: Money = Money.ofMajor(30),
        groupId: Long? = null,
    ): List<Long> = lesson(subjectId, students, start, minutes, rate, groupId)
        .also { ids -> setStatus(ids.first(), ClassStatus.CANCELLED) }

    /** Moves the whole lesson class [id] belongs to into [status]. */
    suspend fun setStatus(id: Long, status: ClassStatus) {
        val session = env.classRepo.getSessionFor(id) ?: error("No lesson for class $id")
        env.classRepo.setStatus(session, status)
    }

    /** Calendar mirroring on, which it is not by default. */
    suspend fun withCalendarSync(invitesEnabled: Boolean = false) {
        env.settingsRepo.update {
            it.copy(calendarSyncEnabled = true, emailInvitesEnabled = invitesEnabled)
        }
    }
}
