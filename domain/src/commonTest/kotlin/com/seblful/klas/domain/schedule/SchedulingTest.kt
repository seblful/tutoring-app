package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
        listOf(
            klass(1, "2026-09-01T10:00:00Z"),
            klass(2, "2026-09-01T10:30:00Z"),
            klass(3, "2026-09-01T09:30:00Z"),
            klass(4, "2026-09-01T08:00:00Z", minutes = 240),
        ).forEach { existing ->
            assertEquals(
                listOf(existing.id),
                Scheduling.conflicts(tenToEleven, listOf(existing)).map { it.first.id },
                "class ${existing.id} should clash",
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
    fun `the lesson being edited is ignored - so it cannot clash with itself`() {
        val itself = klass(1, "2026-09-01T10:00:00Z")
        assertEquals(
            emptyList<ClassSession>(),
            Scheduling.conflicts(tenToEleven, listOf(itself), ignoring = setOf(1L)),
        )
    }

    @Test
    fun `a whole group lesson is ignored by its own ids - not just the tapped class`() {
        val ann = klass(1, "2026-09-01T10:00:00Z", studentId = 1, groupId = 7)
        val bob = klass(2, "2026-09-01T10:00:00Z", studentId = 2, groupId = 7)

        assertEquals(listOf(2L), Scheduling.conflicts(tenToEleven, listOf(ann, bob), setOf(1L)).map { it.first.id })
        assertEquals(
            emptyList<ClassSession>(),
            Scheduling.conflicts(tenToEleven, listOf(ann, bob), ignoring = setOf(1L, 2L)),
        )
    }

    @Test
    fun `a group lesson in the way reads as one conflict - not one per member`() {
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

    @Test
    fun `the window reaches back the length of the longest lesson there can be`() {
        val window = Scheduling.windowFor(tenToEleven)

        assertEquals(1.days, (tenToEleven.start - window.from))
    }

    @Test
    fun `a lesson already under way when the slot opens is inside the window`() {
        val window = Scheduling.windowFor(tenToEleven)
        val containing = klass(4, "2026-09-01T08:00:00Z", minutes = 240)

        assertTrue(containing.start >= window.from, "it must be read")
        assertEquals(listOf(4L), Scheduling.conflicts(tenToEleven, listOf(containing)).map { it.first.id })
    }

    @Test
    fun `the window covers even the longest lesson a single date could hold`() {
        val window = Scheduling.windowFor(tenToEleven)
        val nearlyADay = klass(1, "2026-08-31T10:30:00Z", minutes = 24 * 60 - 1)

        assertTrue(nearlyADay.start >= window.from, "inside the window")
        assertEquals(listOf(1L), Scheduling.conflicts(tenToEleven, listOf(nearlyADay)).map { it.first.id })
    }

    @Test
    fun `the window closes where the slot does - so a back-to-back lesson is outside it`() {
        val window = Scheduling.windowFor(tenToEleven)
        val after = klass(1, "2026-09-01T11:00:00Z") // starts as the slot closes

        assertEquals(tenToEleven.end, window.toExclusive)
        assertTrue(after.start >= window.toExclusive, "not read, and not a clash either")
    }

    @Test
    fun `one window covers every slot of a term - reaching back from the first`() {
        val week = 7.days
        val term = (0..11).map { Scheduling.Slot(tenToEleven.start.plus(week * it), 60) }

        val window = Scheduling.windowFor(term)

        assertEquals(1.days, ((term.first().start) - window.from))
        assertEquals(term.last().end, window.toExclusive)
        term.forEach { slot ->
            val own = Scheduling.windowFor(slot)
            assertTrue(window.from <= own.from && window.toExclusive >= own.toExclusive, "covers ${slot.start}")
        }
    }

    @Test
    fun `a single slot's window is the one-slot case of the same rule`() {
        assertEquals(Scheduling.windowFor(tenToEleven), Scheduling.windowFor(listOf(tenToEleven)))
    }

    @Test
    fun `a window over no slots is a caller's bug`() {
        assertFailsWith<IllegalArgumentException> { Scheduling.windowFor(emptyList()) }
    }

    @Test
    fun `a slot of no length cannot be built at all`() {
        val at = Instant.parse("2026-09-01T10:30:00Z")

        assertFailsWith<IllegalArgumentException>("zero is refused") {
            Scheduling.Slot(at, 0)
        }
        assertFailsWith<IllegalArgumentException>("so is an inverted span") {
            Scheduling.Slot(at, -30)
        }
    }

    @Test
    fun `a stored class of no length is skipped rather than throwing`() {
        val existing = listOf(
            klass(1, "2026-09-01T10:00:00Z").copy(durationMinutes = 0),
            klass(2, "2026-09-01T10:30:00Z"),
        )

        val clashes = Scheduling.conflicts(tenToEleven, existing)

        assertEquals(listOf(2L), clashes.map { it.first.id })
    }
}
