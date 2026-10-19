package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SchedulingTest {

    private val algebra = Subject(1, "Алгебра")
    private val physics = Subject(2, "Физика")
    private val english = Subject(3, "Английский")
    private val all = listOf(algebra, physics, english)

    @Test
    fun `only subjects priced for the student can be booked`() {
        val assignable = Scheduling.assignableSubjects(all, setOf(1L, 3L))
        assertEquals(listOf(algebra, english), assignable)
    }

    @Test
    fun `a student with no priced subjects can book nothing`() {
        assertEquals(emptyList<Subject>(), Scheduling.assignableSubjects(all, emptySet()))
    }

    @Test
    fun `the catalogue order is preserved regardless of assigned-id order`() {
        val assignable = Scheduling.assignableSubjects(all, listOf(3L, 2L, 1L))
        assertEquals(listOf(algebra, physics, english), assignable)
    }

    @Test
    fun `assigned ids with no matching subject are ignored`() {
        val assignable = Scheduling.assignableSubjects(all, setOf(1L, 99L))
        assertEquals(listOf(algebra), assignable)
    }

    @Test
    fun `an empty catalogue yields nothing`() {
        assertEquals(emptyList<Subject>(), Scheduling.assignableSubjects(emptyList(), setOf(1L)))
    }

    // --- Conflicts: one tutor cannot teach two lessons at once ---

    private val tenToEleven = Scheduling.Slot(Instant.parse("2026-09-01T10:00:00Z"), 60)

    private fun klass(
        id: Long,
        start: String,
        minutes: Int = 60,
        status: LessonStatus = LessonStatus.SCHEDULED,
        studentId: Long = 1,
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        start = Instant.parse(start),
        durationMinutes = minutes,
        status = status,
        groupId = groupId,
    )

    @Test
    fun `a free slot conflicts with nothing`() {
        assertEquals(emptyList<ClassSession>(), Scheduling.conflicts(tenToEleven, emptyList()))
    }

    @Test
    fun `back-to-back lessons do not clash`() {
        val before = klass(1, "2026-09-01T09:00:00Z") // 09:00–10:00, ends as the slot opens
        val after = klass(2, "2026-09-01T11:00:00Z") // 11:00–12:00, starts as it closes
        assertEquals(emptyList<ClassSession>(), Scheduling.conflicts(tenToEleven, listOf(before, after)))
    }

    @Test
    fun `every kind of overlap is a conflict`() {
        // Identical, starting inside, ending inside, and wholly containing the slot.
        listOf(
            klass(1, "2026-09-01T10:00:00Z"),
            klass(2, "2026-09-01T10:30:00Z"),
            klass(3, "2026-09-01T09:30:00Z"),
            klass(4, "2026-09-01T08:00:00Z", minutes = 240),
        ).forEach { existing ->
            assertEquals(
                "class ${existing.id} should clash",
                listOf(existing.id),
                Scheduling.conflicts(tenToEleven, listOf(existing)).map { it.first.id },
            )
        }
    }

    @Test
    fun `a completed lesson still holds its hour but a cancelled one gives it back`() {
        val completed = klass(1, "2026-09-01T10:00:00Z", status = LessonStatus.COMPLETED)
        val cancelled = klass(2, "2026-09-01T10:00:00Z", status = LessonStatus.CANCELLED)

        assertEquals(listOf(1L), Scheduling.conflicts(tenToEleven, listOf(completed, cancelled)).map { it.first.id })
        assertEquals(emptyList<ClassSession>(), Scheduling.conflicts(tenToEleven, listOf(cancelled)))
    }

    @Test
    fun `the lesson being edited is ignored, so it cannot clash with itself`() {
        val itself = klass(1, "2026-09-01T10:00:00Z")
        assertEquals(
            emptyList<ClassSession>(),
            Scheduling.conflicts(tenToEleven, listOf(itself), ignoring = setOf(1L)),
        )
    }

    @Test
    fun `a whole group lesson is ignored by its own ids, not just the tapped class`() {
        val ann = klass(1, "2026-09-01T10:00:00Z", studentId = 1, groupId = 7)
        val bob = klass(2, "2026-09-01T10:00:00Z", studentId = 2, groupId = 7)

        // Passing only the tapped member's id leaves the lesson colliding with its own other member.
        assertEquals(listOf(2L), Scheduling.conflicts(tenToEleven, listOf(ann, bob), setOf(1L)).map { it.first.id })
        assertEquals(
            emptyList<ClassSession>(),
            Scheduling.conflicts(tenToEleven, listOf(ann, bob), ignoring = setOf(1L, 2L)),
        )
    }

    @Test
    fun `a group lesson in the way reads as one conflict, not one per member`() {
        val lesson = listOf(
            klass(1, "2026-09-01T10:00:00Z", studentId = 1, groupId = 7),
            klass(2, "2026-09-01T10:00:00Z", studentId = 2, groupId = 7),
            klass(3, "2026-09-01T10:00:00Z", studentId = 3, groupId = 7),
        )
        val clashes = Scheduling.conflicts(tenToEleven, lesson)

        assertEquals(1, clashes.size)
        assertEquals(7L, clashes.single().groupId)
        assertEquals(3, clashes.single().size)
    }

    // --- The window: which classes the rule above ever gets asked about ---

    /**
     * These pin the window's **magnitude**, which is what nothing did while it was a private helper on
     * `ClassRepository`. Its existence was covered — deleting it broke three tests — but shrinking the
     * lead-in from a day to an hour left all 647 green, and the app would then have stopped detecting
     * exactly the containing overlap `every kind of overlap is a conflict` was written to catch: the
     * scan filters on a class's *start*, so a lesson already under way when the slot opens is simply
     * never offered to [Scheduling.conflicts] and the hour reads as free.
     */
    @Test
    fun `the window reaches back the length of the longest lesson there can be`() {
        val window = Scheduling.windowFor(tenToEleven)

        // Deliberately a literal, not `Scheduling.LONGEST_LESSON`: asserting the window against the
        // constant it is derived from restates the implementation and holds for any value of it, which
        // is the shape of the gap this whole section exists to close.
        assertEquals(1.days, (tenToEleven.start - window.from))
    }

    @Test
    fun `a lesson already under way when the slot opens is inside the window`() {
        val window = Scheduling.windowFor(tenToEleven)
        // The 08:00 four-hour lesson from `every kind of overlap is a conflict`, which that test hands
        // to the rule directly — so only this one says it would ever be read from the database.
        val containing = klass(4, "2026-09-01T08:00:00Z", minutes = 240)

        assertTrue("it must be read", containing.start >= window.from)
        assertEquals(listOf(4L), Scheduling.conflicts(tenToEleven, listOf(containing)).map { it.first.id })
    }

    /**
     * The bound is tight, not generous: a lesson's times are wall-clock on one date, so its length is
     * strictly under 24 hours, so anything still running at the slot's start began strictly after
     * `start - LONGEST_LESSON`. A whole day is the smallest window that cannot miss one.
     */
    @Test
    fun `the window covers even the longest lesson a single date could hold`() {
        val window = Scheduling.windowFor(tenToEleven)
        val nearlyADay = klass(1, "2026-08-31T10:30:00Z", minutes = 24 * 60 - 1)

        assertTrue("inside the window", nearlyADay.start >= window.from)
        assertEquals(listOf(1L), Scheduling.conflicts(tenToEleven, listOf(nearlyADay)).map { it.first.id })
    }

    @Test
    fun `the window closes where the slot does, so a back-to-back lesson is outside it`() {
        val window = Scheduling.windowFor(tenToEleven)
        val after = klass(1, "2026-09-01T11:00:00Z") // starts as the slot closes

        assertEquals(tenToEleven.end, window.toExclusive)
        assertTrue("not read, and not a clash either", after.start >= window.toExclusive)
    }

    /**
     * The many-slot window — what booking a whole term reads over, `ClassRepository.bookSeries`' one
     * read for twelve occurrences.
     *
     * It used to be spelled by hand at that call site, so the **write** path shaped its own window while
     * only the read path used this module's. They agreed on the figure and not on the shape, which is the
     * half a later change to [Scheduling.windowFor] would have broken silently — and silently on the path
     * that persists.
     */
    @Test
    fun `one window covers every slot of a term, reaching back from the first`() {
        val week = 7.days
        val term = (0..11).map { Scheduling.Slot(tenToEleven.start.plus(week * it), 60) }

        val window = Scheduling.windowFor(term)

        assertEquals(1.days, ((term.first().start) - window.from))
        assertEquals(term.last().end, window.toExclusive)
        // And it is a superset of each occurrence's own window, which is what makes one read enough.
        term.forEach { slot ->
            val own = Scheduling.windowFor(slot)
            assertTrue("covers ${slot.start}", window.from <= own.from && window.toExclusive >= own.toExclusive)
        }
    }

    @Test
    fun `a single slot's window is the one-slot case of the same rule`() {
        assertEquals(Scheduling.windowFor(tenToEleven), Scheduling.windowFor(listOf(tenToEleven)))
    }

    @Test
    fun `a window over no slots is a caller's bug`() {
        assertThrows(IllegalArgumentException::class.java) { Scheduling.windowFor(emptyList()) }
    }

    /**
     * A slot of no length is now refused outright, rather than tolerated and asked about.
     *
     * This test used to pin the tolerant behaviour — a zero-length slot inside a lesson did report the
     * clash, so the rule looked total. It wasn't: `overlaps` needs `other.start < end`, and for a
     * zero-length slot `end == start`, so the same query placed at a lesson's *own* start instant found
     * nothing. The rule held mid-hour and failed open at the edge, which is the direction that lets a
     * lesson be booked over one already there. There is nothing in the app that wants a zero-length
     * slot, so it stopped being a value that can be made.
     */
    @Test
    fun `a slot of no length cannot be built at all`() {
        val at = Instant.parse("2026-09-01T10:30:00Z")

        assertThrows("zero is refused", IllegalArgumentException::class.java) {
            Scheduling.Slot(at, 0)
        }
        assertThrows("so is an inverted span", IllegalArgumentException::class.java) {
            Scheduling.Slot(at, -30)
        }
    }

    /**
     * The row that should not exist, met on the read path: a stored class of no length is skipped by
     * the conflict scan rather than allowed to throw. One malformed row must not stop the overlap
     * check being made for every other lesson — which is what asking `slotOf` about it would do,
     * inside the flow a booking form collects.
     */
    @Test
    fun `a stored class of no length is skipped rather than throwing`() {
        val existing = listOf(
            klass(1, "2026-09-01T10:00:00Z").copy(durationMinutes = 0),
            klass(2, "2026-09-01T10:30:00Z"),
        )

        val clashes = Scheduling.conflicts(tenToEleven, existing)

        // The good row is still found; the bad one is simply not in anybody's way.
        assertEquals(listOf(2L), clashes.map { it.first.id })
    }
}
