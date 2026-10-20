package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    private fun lessonIdFor(groupId: Long?, start: Instant, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    @Test
    fun `classes of one group at one time collapse into a single session`() {
        val sessions = SessionRules.sessions(
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
        assertEquals(listOf(1L, 2L, 3L), session.classes.map { it.id })
        assertEquals(1L, session.first.id)
    }

    @Test
    fun `individual classes never merge - not even at the same time`() {
        val sessions = SessionRules.sessions(
            listOf(klass(1, studentId = 1), klass(2, studentId = 2)),
        )

        assertEquals(2, sessions.size)
        assertTrue(sessions.none { it.isGroup })
    }

    @Test
    fun `the same group at two times is two sessions`() {
        val sessions = SessionRules.sessions(
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
    fun `two lessons of one group in the same hour stay two lessons`() {
        val sessions = SessionRules.sessions(
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
        val sessions = SessionRules.sessions(
            listOf(klass(9, studentId = 1, start = t1), klass(4, studentId = 2, start = t0)),
        )
        assertEquals(listOf(t0, t1), sessions.map { it.start })
    }

    @Test
    fun `an empty class list yields no sessions`() {
        assertTrue(SessionRules.sessions(emptyList()).isEmpty())
    }

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

    @Test
    fun `a taught lesson with an absentee is taught - and bills only the ones who were there`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.PRESENT),
                klass(2, 2, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT),
                klass(3, 3, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT_BILLED),
            ),
        )

        assertEquals(LessonStatus.COMPLETED, session.status)
        assertEquals(1, session.present, "one of the three was in the room")
        assertEquals(
            listOf(1L, 3L),
            session.classes.filter { it.isBillable }.map { it.id },
            "and two of the three owe for it — the absentee under a late-cancellation policy included",
        )
    }

    @Test
    fun `archived students are not booked into new lessons`() {
        val members = listOf(
            Student(1, "Аня"),
            Student(2, "Борис", archived = true),
        )
        assertEquals(listOf(1L), GroupRules.bookableMembers(members).map { it.id })
    }

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
        assertNull(session.sharedPrice, "differing rates cannot collapse into one")
    }

    @Test
    fun `a scheduled session offers completing and cancelling`() {
        val session = ClassSession(7, listOf(klass(1, 1, groupId = 7), klass(2, 2, groupId = 7)))
        assertEquals(
            setOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED),
            SessionRules.sessionTransitions(session),
        )
    }

    @Test
    fun `a lesson offers the transitions of its own status - whatever its members attended`() {
        val session = ClassSession(
            7,
            listOf(
                klass(1, 1, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.PRESENT),
                klass(2, 2, groupId = 7, status = LessonStatus.COMPLETED, attended = Attended.ABSENT),
            ),
        )

        assertEquals(
            setOf(LessonStatus.CANCELLED),
            SessionRules.sessionTransitions(session),
            "a taught lesson can be called off, and not un-taught",
        )
        assertFalse(ClassRules.canTransition(LessonStatus.COMPLETED, LessonStatus.SCHEDULED))
    }

    @Test
    fun `a session edit puts every member on the lesson's one rate`() {
        // The members' classes were booked at different rates (a lesson from before a group priced
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val bob = klass(2, studentId = 2, groupId = 7, price = Money.ofMajor(25))
        val changes = SessionEdit(
            subjectId = 9,
            durationMinutes = 90,
            mode = ClassMode.REMOTE,
            price = Money.ofMajor(20),
        )

        val edits = SessionRules.editsFor(ClassSession(7, listOf(ann, bob)), changes).map { it.second }

        assertEquals(listOf(Money.ofMajor(20), Money.ofMajor(20)), edits.map { it.price })
        assertEquals(9L, edits.first().subjectId)
        assertEquals(90, edits.first().durationMinutes)
        assertEquals(ClassMode.REMOTE, edits.first().mode)
        assertEquals(listOf(1L, 2L), edits.map { it.studentId })
    }

    @Test
    fun `a lesson may be re-priced to zero`() {
        val ann = klass(1, studentId = 1, groupId = 7, price = Money.ofMajor(30))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            price = Money.ZERO,
        )
        val edit = SessionRules.editsFor(ClassSession(7, listOf(ann)), changes).single().second
        assertEquals(Money.ZERO, edit.price)
    }

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

        val edits = SessionRules.editsFor(session, changes)

        assertEquals(listOf(1L, 2L), edits.map { (c, _) -> c.id })
        assertEquals(listOf(Money.ofMajor(20), Money.ofMajor(20)), edits.map { (_, e) -> e.price })
        assertEquals(listOf(1L, 2L), edits.map { (_, e) -> e.studentId })
    }

    @Test
    fun `an individual class can be re-attributed - and takes the new owner's rate`() {
        val session = ClassSession.of(klass(1, studentId = 1, price = Money.ofMajor(30)))
        val changes = SessionEdit(
            subjectId = 1,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            price = Money.ofMajor(45),
            studentId = 2,
        )

        val (_, edit) = SessionRules.editsFor(session, changes).single()

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

        assertFailsWith<IllegalArgumentException>(
            "who was in a lesson is changed by adding and removing classes",
        ) { SessionRules.editsFor(session, changes) }
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
        assertTrue(change.joining.isEmpty(), "nobody joined")
        assertFalse(change.isEmpty)
    }

    @Test
    fun `a member wanted without a class gets one at the lesson's own terms`() {
        val session = groupSession(1L to 1L)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 5), lessonEdit, t1)

        assertTrue(change.dropped.isEmpty(), "nobody left")
        val joined = change.joining.single()
        assertEquals(5L, joined.studentId)
        assertEquals(9L, joined.subjectId)
        assertEquals(t1, joined.start)
        assertEquals(90, joined.durationMinutes)
        assertEquals(Money.ofMajor(20), joined.price)
        assertEquals(ClassMode.REMOTE, joined.mode)
        assertEquals(7L, joined.groupId)
        assertEquals(0L, joined.id, "no id yet — it has never been written")
    }

    @Test
    fun `joining a completed lesson joins it completed`() {
        val session = groupSession(1L to 1L, status = LessonStatus.COMPLETED)

        val change = GroupRules.rosterChange(session, wanted = setOf(1, 5), lessonEdit, t0)

        assertEquals(LessonStatus.COMPLETED, change.joining.single().status)
    }

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
        assertEquals(listOf(1L), change.billedDropped.map { it.id }, "only the completed one had earned")
    }

    @Test
    fun `a lesson cannot be emptied of everybody`() {
        val session = groupSession(1L to 1L)

        assertFailsWith<IllegalArgumentException>(
            "a lesson with nobody in it bills no one",
        ) { GroupRules.rosterChange(session, wanted = emptySet(), lessonEdit, t0) }
    }

    @Test
    fun `an individual lesson has no roster to reconcile`() {
        val session = ClassSession.of(klass(1, studentId = 1))

        assertFailsWith<IllegalArgumentException>(
            "re-attributing an individual class is SessionEdit.studentId's job",
        ) { GroupRules.rosterChange(session, wanted = setOf(2), lessonEdit, t0) }
    }

    // --- The lessons one student is in — the schedule's per-student narrowing --------------------

    @Test
    fun `a student's own lessons come through`() {
        val theirs = klass(1, studentId = 1)
        val anothers = klass(2, studentId = 2, start = t1)

        assertEquals(
            listOf(theirs),
            SessionRules.lessonsWith(listOf(theirs, anothers), studentId = 1),
        )
    }

    @Test
    fun `a student in nothing here matches nothing`() {
        val classes = listOf(klass(1, studentId = 1), klass(2, studentId = 2, start = t1))

        assertEquals(emptyList<TutoringClass>(), SessionRules.lessonsWith(classes, studentId = 3))
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

        assertEquals(classes, SessionRules.lessonsWith(classes, studentId = 1))
    }

    @Test
    fun `a group lesson the student is not in is left out entirely`() {
        val classes = groupSession(1L to 1L, 2L to 2L).classes

        assertEquals(emptyList<TutoringClass>(), SessionRules.lessonsWith(classes, studentId = 3))
    }

    @Test
    fun `a mixed day keeps every lesson the student is in and nothing else`() {
        val group = groupSession(1L to 1L, 2L to 2L).classes
        val theirOwn = klass(10, studentId = 1, start = t1)
        val someoneElses = klass(11, studentId = 2, start = t1.plus(7200.seconds))
        val all = group + theirOwn + someoneElses

        assertEquals(group + theirOwn, SessionRules.lessonsWith(all, studentId = 1))
    }

    // --- lessonEdit: the composition, which is what had no test ---

    private val edit90 = SessionEdit(
        subjectId = 9,
        durationMinutes = 90,
        mode = ClassMode.REMOTE,
        price = Money.ofMajor(20),
    )

    @Test
    fun `a lesson edit states the lesson's own row - whoever stays in it`() {
        // The whole roster leaves: Ann and Bob out, Cara in. There is no *updated* class left, which is
        // the case that used to leave the lesson row unwritten — and so left the lesson at its old hour.
        val session = groupSession(1L to 1L, 2L to 2L)

        val edit = SessionRules.lessonEdit(session, edit90, newStart = t1, now = t0, roster = setOf(3L))

        assertEquals(emptyList<TutoringClass>(), edit.updated, "nobody stayed")
        assertEquals(listOf(1L, 2L), edit.dropped.map { it.studentId })
        assertEquals(listOf(3L), edit.joining.map { it.studentId })
        // And the lesson still says what it now is.
        assertEquals(t1, edit.lesson.start)
        assertEquals(90, edit.lesson.durationMinutes)
        assertEquals(9L, edit.lesson.subjectId)
        assertEquals(ClassMode.REMOTE, edit.lesson.mode)
        assertEquals(session.lessonId, edit.lesson.lessonId)
    }

    @Test
    fun `the lesson row a partial roster change states matches the one a total change states`() {
        // The lesson's own fields are shared by every class, so which classes survive must not change
        // them. This is the invariant that makes naming `lesson` separately safe rather than arbitrary.
        val session = groupSession(1L to 1L, 2L to 2L)

        val partial = SessionRules.lessonEdit(session, edit90, t1, t0, roster = setOf(1L))
        val total = SessionRules.lessonEdit(session, edit90, t1, t0, roster = setOf(3L))
        val untouched = SessionRules.lessonEdit(session, edit90, t1, t0, roster = null)

        val lessonFacts = { c: TutoringClass ->
            listOf(c.lessonId, c.start, c.durationMinutes, c.subjectId, c.mode, c.status, c.groupId)
        }
        assertEquals(lessonFacts(untouched.lesson), lessonFacts(partial.lesson))
        assertEquals(lessonFacts(untouched.lesson), lessonFacts(total.lesson))
    }

    @Test
    fun `a null roster edits every class and drops nobody`() {
        val session = groupSession(1L to 1L, 2L to 2L)

        val edit = SessionRules.lessonEdit(session, edit90, newStart = t0, now = t1, roster = null)

        assertEquals(listOf(1L, 2L), edit.updated.map { it.studentId })
        assertTrue(edit.dropped.isEmpty())
        assertTrue(edit.joining.isEmpty())
        assertEquals(listOf(90, 90), edit.updated.map { it.durationMinutes }, "every class carries the edit")
    }

    @Test
    fun `a lesson edit refuses to re-attribute a group lesson`() {
        // The refusal is `editsFor`'s and it still applies through the composed call — which is the
        // reason the per-class step is private.
        val session = groupSession(1L to 1L, 2L to 2L)
        val reattributed = edit90.copy(studentId = 3L)

        assertFailsWith<IllegalArgumentException> {
            SessionRules.lessonEdit(session, reattributed, newStart = t0, now = t1)
        }
    }

    // --- offeredRoster: one union, five call sites ---

    private val everyone = listOf(
        Student(1, "Аня"),
        Student(2, "Борис", archived = true),
        Student(3, "Вера"),
        Student(4, "Глеб"),
    )

    @Test
    fun `a new lesson is offered the group's bookable members only`() {
        // Borís is archived: a group they are in must stop billing them.
        assertEquals(
            listOf(1L, 3L),
            GroupRules.offeredRoster(everyone, memberIds = setOf(1L, 2L, 3L)).map { it.id },
        )
    }

    @Test
    fun `an existing lesson is also offered whoever it already covers`() {
        // Borís was in this lesson and has since been archived; Gleb was in it and has since left the
        // group. Both stay listed, so both can still be *unticked* — which is what removes their class.
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            GroupRules.offeredRoster(everyone, memberIds = setOf(1L, 3L), alreadyIn = setOf(2L, 4L))
                .map { it.id },
        )
    }

    @Test
    fun `the offer keeps the order it was given - not the order it was assembled in`() {
        // Filtered out of the full list rather than concatenated: the caller's list is name-ordered, and
        // a union that appended the strays would list them after everyone else.
        val reversed = everyone.reversed()

        assertEquals(
            listOf(4L, 3L, 2L),
            GroupRules.offeredRoster(reversed, memberIds = setOf(3L, 4L), alreadyIn = setOf(2L)).map { it.id },
        )
    }

    // ── ClassSession.attendance ──────────────────────────────────────────────────────────────────
    // «N из M» is said only about a lesson that happened. This was an `if` combining `present` and
    // `size` inside a LazyColumn item lambda on the group card, where nothing could reach it.

    private fun session(status: LessonStatus, vararg attended: Attended) = ClassSession(
        groupId = 7,
        classes = attended.mapIndexed { i, a ->
            klass(id = i + 1L, studentId = i + 1L, groupId = 7, status = status, attended = a)
        },
    )

    @Test
    fun `a lesson still to come names its whole roster`() {
        // Nobody has missed anything yet, so counting who "came" would just be the roster with a
        // smaller-looking number beside it.
        val scheduled = session(LessonStatus.SCHEDULED, Attended.EXPECTED, Attended.EXPECTED, Attended.EXPECTED)

        assertEquals(Attendance.All(3), scheduled.attendance)
    }

    @Test
    fun `a taught lesson everybody attended names its whole roster`() {
        val taught = session(LessonStatus.COMPLETED, Attended.PRESENT, Attended.PRESENT)

        assertEquals(Attendance.All(2), taught.attendance)
    }

    @Test
    fun `a taught lesson somebody missed says how many of how many`() {
        val taught = session(LessonStatus.COMPLETED, Attended.PRESENT, Attended.ABSENT, Attended.PRESENT)

        assertEquals(Attendance.Some(present = 2, of = 3), taught.attendance)
    }

    /**
     * Absent and charged anyway is still absent: [Attended.ABSENT_BILLED] is a fact about the money, not
     * about who was in the room.
     */
    @Test
    fun `being billed for missing it does not make you present`() {
        val taught = session(LessonStatus.COMPLETED, Attended.PRESENT, Attended.ABSENT_BILLED)

        assertEquals(Attendance.Some(present = 1, of = 2), taught.attendance)
    }

    /** A called-off lesson names the roster it was for — nobody attended it, and that is not news. */
    @Test
    fun `a cancelled lesson names its roster rather than counting nobody`() {
        val cancelled = session(LessonStatus.CANCELLED, Attended.EXPECTED, Attended.EXPECTED)

        assertEquals(Attendance.All(2), cancelled.attendance)
    }
}
