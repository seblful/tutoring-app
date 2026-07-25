package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GroupRulesTest {

    private val t0 = Instant.parse("2026-09-01T09:00:00Z")
    private val t1 = Instant.parse("2026-09-01T11:00:00Z")

    private fun klass(
        id: Long,
        studentId: Long,
        start: Instant = t0,
        groupId: Long? = null,
        status: ClassStatus = ClassStatus.SCHEDULED,
        price: Money = Money.ofMajor(30),
    ) = TutoringClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        price = price,
        status = status,
        groupId = groupId,
    )

    // --- sessions ---

    @Test
    fun `classes of one group at one time collapse into a single session`() {
        val sessions = GroupRules.sessions(
            listOf(
                klass(3, studentId = 3, groupId = 7),
                klass(1, studentId = 1, groupId = 7),
                klass(2, studentId = 2, groupId = 7),
            ),
        )

        val session = sessions.single()
        assertTrue(session.isGroup)
        assertEquals(7L, session.groupId)
        assertEquals(3, session.size)
        // Ordered by id, so the representative class is stable across re-emissions.
        assertEquals(listOf(1L, 2L, 3L), session.classes.map { it.id })
        assertEquals(1L, session.first.id)
    }

    @Test
    fun `individual classes never merge, not even at the same time`() {
        val sessions = GroupRules.sessions(
            listOf(klass(1, studentId = 1), klass(2, studentId = 2)),
        )

        assertEquals(2, sessions.size)
        assertTrue(sessions.none { it.isGroup })
    }

    @Test
    fun `the same group at two times is two sessions`() {
        val sessions = GroupRules.sessions(
            listOf(
                klass(1, studentId = 1, groupId = 7, start = t0),
                klass(2, studentId = 2, groupId = 7, start = t0),
                klass(3, studentId = 1, groupId = 7, start = t1),
            ),
        )

        assertEquals(2, sessions.size)
        assertEquals(listOf(t0, t1), sessions.map { it.start })
        assertEquals(listOf(2, 1), sessions.map { it.size })
    }

    @Test
    fun `a member moved on their own leaves the session`() {
        // The session key is (group, start), so a different start is a different lesson — no stale
        // membership flag to reconcile.
        val sessions = GroupRules.sessions(
            listOf(
                klass(1, studentId = 1, groupId = 7, start = t0),
                klass(2, studentId = 2, groupId = 7, start = t0),
                klass(3, studentId = 3, groupId = 7, start = t1),
            ),
        )

        assertEquals(2, sessions.size)
        assertEquals(2, sessions.first().size)
        assertEquals(listOf(3L), sessions.last().classes.map { it.id })
    }

    @Test
    fun `sessions come out ordered by start`() {
        val sessions = GroupRules.sessions(
            listOf(klass(9, studentId = 1, start = t1), klass(4, studentId = 2, start = t0)),
        )
        assertEquals(listOf(t0, t1), sessions.map { it.start })
    }

    @Test
    fun `an empty class list yields no sessions`() {
        assertTrue(GroupRules.sessions(emptyList()).isEmpty())
    }

    // --- commonStatus ---

    @Test
    fun `a session shares a status until a member is corrected on their own`() {
        val completed = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = ClassStatus.COMPLETED),
                klass(2, 2, groupId = 7, status = ClassStatus.COMPLETED),
            ),
        )
        assertEquals(ClassStatus.COMPLETED, completed.commonStatus)

        val oneAbsent = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = ClassStatus.COMPLETED),
                klass(2, 2, groupId = 7, status = ClassStatus.CANCELLED),
            ),
        )
        assertNull("a mixed session has no single status", oneAbsent.commonStatus)
    }

    // --- bookableMembers ---

    @Test
    fun `archived students are not booked into new lessons`() {
        val members = listOf(
            Student(1, "Аня"),
            Student(2, "Борис", archived = true),
        )
        assertEquals(listOf(1L), GroupRules.bookableMembers(members).map { it.id })
    }

    // --- bookableSubjects ---

    private val allSubjects = listOf(Subject(1, "Алгебра"), Subject(2, "Физика"), Subject(3, "Химия"))

    @Test
    fun `a group can only be booked for a subject every member has a price for`() {
        val bookable = GroupRules.bookableSubjects(
            allSubjects,
            listOf(setOf(1L, 2L), setOf(2L, 3L), setOf(2L)),
        )
        assertEquals(listOf(2L), bookable.map { it.id })
    }

    @Test
    fun `with no shared priced subject nothing is bookable`() {
        val bookable = GroupRules.bookableSubjects(allSubjects, listOf(setOf(1L), setOf(2L)))
        assertTrue(bookable.isEmpty())
    }

    @Test
    fun `an empty roster can be booked for nothing`() {
        assertTrue(GroupRules.bookableSubjects(allSubjects, emptyList()).isEmpty())
    }

    @Test
    fun `the subject order of the picker is preserved`() {
        val bookable = GroupRules.bookableSubjects(allSubjects, listOf(setOf(3L, 1L), setOf(1L, 3L)))
        assertEquals(listOf(1L, 3L), bookable.map { it.id })
    }

    // --- session status changes ---

    @Test
    fun `a scheduled session offers completing and cancelling`() {
        val session = ClassSession(7, listOf(klass(1, 1, groupId = 7), klass(2, 2, groupId = 7)))
        assertEquals(
            setOf(ClassStatus.COMPLETED, ClassStatus.CANCELLED),
            GroupRules.sessionTransitions(session),
        )
    }

    @Test
    fun `completing a partly cancelled session applies only to the classes it is legal for`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = ClassStatus.SCHEDULED),
                klass(2, 2, groupId = 7, status = ClassStatus.CANCELLED),
                klass(3, 3, groupId = 7, status = ClassStatus.COMPLETED),
            ),
        )

        val applicable = GroupRules.applicableTo(session, ClassStatus.COMPLETED)

        // The absentee stays cancelled (cancelled -> completed isn't a legal transition) and the
        // already-completed member is left alone.
        assertEquals(listOf(1L), applicable.map { it.id })
        assertFalse(ClassRules.canTransition(ClassStatus.CANCELLED, ClassStatus.COMPLETED))
    }

    // --- editFor ---

    @Test
    fun `a session edit keeps each member on their own rate`() {
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val bob = klass(2, studentId = 2, groupId = 7, price = Money.ofMajor(25))
        val changes = SessionEdit(
            subjectId = 9,
            durationMinutes = 90,
            mode = ClassMode.REMOTE,
            pricesByStudent = mapOf(1L to Money.ofMajor(35), 2L to Money.ofMajor(28)),
        )

        assertEquals(Money.ofMajor(35), GroupRules.editFor(ann, changes).price)
        assertEquals(Money.ofMajor(28), GroupRules.editFor(bob, changes).price)
        // Shared fields come from the edit; the student is never re-attributed by a session edit.
        assertEquals(9L, GroupRules.editFor(ann, changes).subjectId)
        assertEquals(90, GroupRules.editFor(ann, changes).durationMinutes)
        assertEquals(ClassMode.REMOTE, GroupRules.editFor(ann, changes).mode)
        assertEquals(1L, GroupRules.editFor(ann, changes).studentId)
    }

    @Test
    fun `a member missing from the price map keeps the rate their class was booked at`() {
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            pricesByStudent = emptyMap(),
        )
        assertEquals(Money.ofMajor(30), GroupRules.editFor(ann, changes).price)
    }

    // --- editsFor: one edit, fanned out over the lesson ---

    @Test
    fun `one edit fans out to every class of the lesson`() {
        val session = ClassSession(
            7,
            listOf(klass(1, studentId = 1, groupId = 7), klass(2, studentId = 2, groupId = 7)),
        )
        val changes = SessionEdit(
            subjectId = 9,
            durationMinutes = 90,
            mode = ClassMode.REMOTE,
            pricesByStudent = mapOf(1L to Money.ofMajor(35), 2L to Money.ofMajor(28)),
        )

        val edits = GroupRules.editsFor(session, changes)

        assertEquals(listOf(1L, 2L), edits.map { (c, _) -> c.id })
        assertEquals(listOf(Money.ofMajor(35), Money.ofMajor(28)), edits.map { (_, e) -> e.price })
        assertEquals(listOf(1L, 2L), edits.map { (_, e) -> e.studentId })
    }

    @Test
    fun `an individual class can be re-attributed, and takes the new owner's rate`() {
        val session = ClassSession.of(klass(1, studentId = 1, price = Money.ofMajor(30)))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            pricesByStudent = mapOf(2L to Money.ofMajor(45)),
            studentId = 2,
        )

        val (_, edit) = GroupRules.editsFor(session, changes).single()

        assertEquals(2L, edit.studentId)
        assertEquals(Money.ofMajor(45), edit.price)
    }

    @Test
    fun `a group lesson cannot be handed to another student by editing it`() {
        val session = ClassSession(
            7,
            listOf(klass(1, studentId = 1, groupId = 7), klass(2, studentId = 2, groupId = 7)),
        )
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            pricesByStudent = mapOf(3L to Money.ofMajor(20)),
            studentId = 3,
        )

        var threw = false
        try {
            GroupRules.editsFor(session, changes)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("membership is changed on the roster, not by editing one of its lessons", threw)
    }
}
