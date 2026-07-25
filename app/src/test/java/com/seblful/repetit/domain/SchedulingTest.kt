package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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
        status: ClassStatus = ClassStatus.SCHEDULED,
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
        val completed = klass(1, "2026-09-01T10:00:00Z", status = ClassStatus.COMPLETED)
        val cancelled = klass(2, "2026-09-01T10:00:00Z", status = ClassStatus.CANCELLED)

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

    @Test
    fun `a zero-length slot cannot be squeezed between lessons`() {
        // The form refuses a non-positive range before this point; pinned so the rule stays total.
        val instant = Scheduling.Slot(Instant.parse("2026-09-01T10:30:00Z"), 0)
        assertEquals(listOf(1L), Scheduling.conflicts(instant, listOf(klass(1, "2026-09-01T10:00:00Z"))).map { it.first.id })
    }
}
