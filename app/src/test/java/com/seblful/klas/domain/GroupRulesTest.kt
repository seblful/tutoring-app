package com.seblful.klas.domain

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        status: LessonStatus = LessonStatus.SCHEDULED,
        attended: Attended = if (status.isBillable) Attended.PRESENT else Attended.EXPECTED,
        price: Money = Money.ofMajor(30),
        /** Which lesson this class is part of — see [lessonIdFor]. */
        lessonId: Long = lessonIdFor(groupId, start, id),
    ) = TutoringClass(
        id = id,
        lessonId = lessonId,
        studentId = studentId,
        attended = attended,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        price = price,
        status = status,
        groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Instant>, Long>()

    /**
     * The lesson a fixture's class belongs to: one per (group, start) for a group's, and its own id for
     * an individual class.
     *
     * A convention of this test, not of the app — storage has a column for it. It exists because that
     * column is the whole of what makes classes one lesson, so a fixture leaving them all at 0 would
     * collapse every class here into a single lesson, and one that numbered them per class would make a
     * group lesson n lessons.
     */
    private fun lessonIdFor(groupId: Long?, start: Instant, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

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

    /**
     * A class belongs to the lesson its `lessonId` names — **not** to whatever else shares its hour.
     *
     * The session key used to be `(groupId, start)`, which meant a member's class edited on its own left
     * the session by definition. That was described as a virtue: no membership flag to go stale. It was
     * the opposite — two rows of one lesson could disagree about when the lesson was, and then they were
     * two lessons of the same group in the same hour, each blocking the other's. Here that state is what
     * the column tells apart, and the start is the lesson's to hold.
     */
    @Test
    fun `two lessons of one group in the same hour stay two lessons`() {
        val sessions = GroupRules.sessions(
            listOf(
                klass(1, studentId = 1, groupId = 7, start = t0, lessonId = 500),
                klass(2, studentId = 2, groupId = 7, start = t0, lessonId = 500),
                klass(3, studentId = 3, groupId = 7, start = t0, lessonId = 501),
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

    // --- status ---

    /** A session of members who agree has their status, and only ever one of the three. */
    @Test
    fun `a session in one status has that status`() {
        val completed = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = LessonStatus.COMPLETED),
                klass(2, 2, groupId = 7, status = LessonStatus.COMPLETED),
            ),
        )
        assertEquals(LessonStatus.COMPLETED, completed.status)
    }

    /**
     * A lesson taught with somebody missing is **taught**, and the status is not a vote.
     *
     * This is where the model earns its keep. The status used to be per student, so this lesson had none
     * of its own — first answered as null and badged «Разные», then as the strictest status among its
     * members. Now the hour holds one status and each student holds whether they were in it, so the two
     * facts can both be true without either being derived from the other.
     */
    @Test
    fun `a taught lesson with an absentee is taught, and bills only the ones who were there`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.PRESENT),
                klass(2, 2, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT),
                klass(3, 3, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT_BILLED),
            ),
        )

        assertEquals(LessonStatus.COMPLETED, session.status)
        assertEquals("one of the three was in the room", 1, session.present)
        assertEquals(
            "and two of the three owe for it — the absentee under a late-cancellation policy included",
            listOf(1L, 3L),
            session.classes.filter { it.isBillable }.map { it.id },
        )
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

    // --- sharedPrice ---

    @Test
    fun `a lesson booked at one rate reports it`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, price = Money.ofMajor(20)),
                klass(2, 2, groupId = 7, price = Money.ofMajor(20)),
            ),
        )
        assertEquals(Money.ofMajor(20), session.sharedPrice)
    }

    @Test
    fun `a legacy lesson whose members were billed differently has no single rate`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, price = Money.ofMajor(20)),
                klass(2, 2, groupId = 7, price = Money.ofMajor(30)),
            ),
        )
        assertNull("differing rates cannot collapse into one", session.sharedPrice)
    }

    // --- session status changes ---

    @Test
    fun `a scheduled session offers completing and cancelling`() {
        val session = ClassSession(7, listOf(klass(1, 1, groupId = 7), klass(2, 2, groupId = 7)))
        assertEquals(
            setOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED),
            GroupRules.sessionTransitions(session),
        )
    }

    /**
     * A lesson offers what **its own** status may become, because that is the only status it has.
     *
     * This used to be the union over its members, with an `applicableTo` beside it picking out the ones a
     * change was legal for — machinery for a lesson whose members held different statuses, which is
     * exactly what the split made unrepresentable. Who was in the room is attendance now, and the members
     * of one lesson cannot disagree about whether it happened.
     */
    @Test
    fun `a lesson offers the transitions of its own status, whatever its members attended`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.PRESENT),
                klass(2, 2, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT),
            ),
        )

        assertEquals(
            "a taught lesson can be called off, and not un-taught",
            setOf(LessonStatus.CANCELLED),
            GroupRules.sessionTransitions(session),
        )
        assertFalse(ClassRules.canTransition(LessonStatus.COMPLETED, LessonStatus.SCHEDULED))
    }

    // --- editFor ---

    @Test
    fun `a session edit puts every member on the lesson's one rate`() {
        // The members' classes were booked at different rates (a lesson from before a group priced
        // itself); re-pricing the lesson brings them all onto the group's rate.
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val bob = klass(2, studentId = 2, groupId = 7, price = Money.ofMajor(25))
        val changes = SessionEdit(
            subjectId = 9,
            durationMinutes = 90,
            mode = ClassMode.REMOTE,
            price = Money.ofMajor(20),
        )

        assertEquals(Money.ofMajor(20), GroupRules.editFor(ann, changes).price)
        assertEquals(Money.ofMajor(20), GroupRules.editFor(bob, changes).price)
        // Shared fields come from the edit; the student is never re-attributed by a session edit.
        assertEquals(9L, GroupRules.editFor(ann, changes).subjectId)
        assertEquals(90, GroupRules.editFor(ann, changes).durationMinutes)
        assertEquals(ClassMode.REMOTE, GroupRules.editFor(ann, changes).mode)
        assertEquals(1L, GroupRules.editFor(ann, changes).studentId)
    }

    @Test
    fun `a lesson may be re-priced to zero`() {
        // Zero is a real rate (a lesson given for free), not "no rate given", so it must survive the
        // edit rather than being taken for a gap and replaced by what the class already carried.
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            price = Money.ZERO,
        )
        assertEquals(Money.ZERO, GroupRules.editFor(ann, changes).price)
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
            price = Money.ofMajor(20),
        )

        val edits = GroupRules.editsFor(session, changes)

        assertEquals(listOf(1L, 2L), edits.map { (c, _) -> c.id })
        assertEquals(listOf(Money.ofMajor(20), Money.ofMajor(20)), edits.map { (_, e) -> e.price })
        assertEquals(listOf(1L, 2L), edits.map { (_, e) -> e.studentId })
    }

    @Test
    fun `an individual class can be re-attributed, and takes the new owner's rate`() {
        val session = ClassSession.of(klass(1, studentId = 1, price = Money.ofMajor(30)))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            price = Money.ofMajor(45),
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
            price = Money.ofMajor(20),
            studentId = 3,
        )

        assertThrows(
            "who was in a lesson is changed by adding and removing classes",
            IllegalArgumentException::class.java,
        ) { GroupRules.editsFor(session, changes) }
    }

    // --- rosterChange: correcting who was in a lesson ---

    private val lessonEdit = SessionEdit(
        subjectId = 9,
        durationMinutes = 90,
        mode = ClassMode.REMOTE,
        price = Money.ofMajor(20),
    )

    private fun groupSession(
        vararg members: Pair<Long, Long>,
        status: LessonStatus = LessonStatus.SCHEDULED,
    ) = ClassSession(
        7,
        members.map { (id, studentId) -> klass(id, studentId = studentId, groupId = 7, status = status) },
    )

    @Test
    fun `a member left out of the wanted roster loses their class`() {
        val session = groupSession(1L to 1L, 2L to 2L, 3L to 3L)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 3), lessonEdit, t0)

        assertEquals(listOf(2L), change.dropped.map { it.id })
        assertTrue("nobody joined", change.joining.isEmpty())
        assertFalse(change.isEmpty)
    }

    @Test
    fun `a member wanted without a class gets one at the lesson's own terms`() {
        val session = groupSession(1L to 1L)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 5), lessonEdit, t1)

        assertTrue("nobody left", change.dropped.isEmpty())
        val joined = change.joining.single()
        assertEquals(5L, joined.studentId)
        // The lesson's terms, not the group's current ones: it is this lesson they are joining.
        assertEquals(9L, joined.subjectId)
        assertEquals(t1, joined.start)
        assertEquals(90, joined.durationMinutes)
        assertEquals(Money.ofMajor(20), joined.price)
        assertEquals(ClassMode.REMOTE, joined.mode)
        assertEquals(7L, joined.groupId)
        assertEquals("no id yet — it has never been written", 0L, joined.id)
    }

    /**
     * The point of editing attendance after the fact: somebody who was there but never ticked owes
     * for the lesson exactly as the others do, so their class joins in the status the lesson is in.
     */
    @Test
    fun `joining a completed lesson joins it completed`() {
        val session = groupSession(1L to 1L, status = LessonStatus.COMPLETED)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 5), lessonEdit, t0)

        assertEquals(LessonStatus.COMPLETED, change.joining.single().status)
    }

    /**
     * A joiner takes the lesson's status even where the members disagree — this lesson was taught, so
     * somebody added to it was in a lesson that was taught, and owes for it. They used to start
     * scheduled, which recorded a group lesson as still to come for one person after the fact.
     */
    @Test
    fun `joining a lesson whose members disagree takes the lesson's own status`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, studentId = 1, groupId = 7, status = LessonStatus.COMPLETED),
                klass(2, studentId = 2, groupId = 7, status = LessonStatus.CANCELLED),
            ),
        )

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 2, 5), lessonEdit, t0)

        assertEquals(LessonStatus.COMPLETED, session.status)
        assertEquals(LessonStatus.COMPLETED, change.joining.single().status)
    }

    @Test
    fun `an unchanged roster changes nothing`() {
        val session = groupSession(1L to 1L, 2L to 2L)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 2), lessonEdit, t0)

        assertTrue(change.isEmpty)
    }

    /** Dropping a billed class takes income with it, so it is named separately for the warning. */
    @Test
    fun `only the billed leavers count as costing anything`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, studentId = 1, groupId = 7, status = LessonStatus.COMPLETED),
                klass(2, studentId = 2, groupId = 7, status = LessonStatus.SCHEDULED),
                klass(3, studentId = 3, groupId = 7, status = LessonStatus.COMPLETED),
            ),
        )

        val change = GroupRules.rosterChange(session, wanted = setOf(3), lessonEdit, t0)

        assertEquals(listOf(1L, 2L), change.dropped.map { it.id })
        assertEquals("only the completed one had earned", listOf(1L), change.billedDropped.map { it.id })
    }

    @Test
    fun `a lesson cannot be emptied of everybody`() {
        val session = groupSession(1L to 1L)

        assertThrows(
            "a lesson with nobody in it bills no one",
            IllegalArgumentException::class.java,
        ) { GroupRules.rosterChange(session, wanted = emptySet(), lessonEdit, t0) }
    }

    @Test
    fun `an individual lesson has no roster to reconcile`() {
        val session = ClassSession.of(klass(1, studentId = 1))

        assertThrows(
            "re-attributing an individual class is SessionEdit.studentId's job",
            IllegalArgumentException::class.java,
        ) { GroupRules.rosterChange(session, wanted = setOf(2), lessonEdit, t0) }
    }

    // --- The lessons one student is in — the schedule's per-student narrowing --------------------

    @Test
    fun `a student's own lessons come through`() {
        val theirs = klass(1, studentId = 1)
        val anothers = klass(2, studentId = 2, start = t1)

        assertEquals(
            listOf(theirs),
            GroupRules.lessonsWith(listOf(theirs, anothers), studentId = 1),
        )
    }

    @Test
    fun `a student in nothing here matches nothing`() {
        val classes = listOf(klass(1, studentId = 1), klass(2, studentId = 2, start = t1))

        assertEquals(emptyList<TutoringClass>(), GroupRules.lessonsWith(classes, studentId = 3))
    }

    /**
     * **The whole lesson, not their class out of it.** This is what keeps a filtered group lesson from
     * arriving as a session of one — which would draw a group row with a roster of one, total one
     * member's money for the day, and hand the action sheet a session the lesson does not consist of.
     */
    @Test
    fun `a group lesson the student is in comes through whole`() {
        val session = groupSession(1L to 1L, 2L to 2L, 3L to 3L)
        val classes = session.classes

        assertEquals(classes, GroupRules.lessonsWith(classes, studentId = 1))
    }

    @Test
    fun `a group lesson the student is not in is left out entirely`() {
        val classes = groupSession(1L to 1L, 2L to 2L).classes

        assertEquals(emptyList<TutoringClass>(), GroupRules.lessonsWith(classes, studentId = 3))
    }

    @Test
    fun `a mixed day keeps every lesson the student is in and nothing else`() {
        val group = groupSession(1L to 1L, 2L to 2L).classes
        val theirOwn = klass(10, studentId = 1, start = t1)
        val someoneElses = klass(11, studentId = 2, start = t1.plusSeconds(7200))
        val all = group + theirOwn + someoneElses

        assertEquals(group + theirOwn, GroupRules.lessonsWith(all, studentId = 1))
    }
}
