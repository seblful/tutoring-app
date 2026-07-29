package com.seblful.klas.domain

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Which kind of lesson a class is, and the narrowing a mixed list offers.
 *
 * The one trap worth a test of its own: a single member's class read as a session of one — the shape
 * a per-member correction uses — must still be a **group** lesson, because it is.
 */
class LessonKindTest {

    private val start = Instant.parse("2026-06-20T10:00:00Z")

    private fun klass(
        id: Long,
        groupId: Long? = null,
        status: LessonStatus = LessonStatus.COMPLETED,
        charge: Money = Money.ofMajor(30),
    ) = TutoringClass(
        id = id,
        lessonId = lessonIdFor(groupId, start, id),
        studentId = id,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        status = status,
        charge = if (status.isBillable) charge else Money.ZERO,
        groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    /**
     * The lesson a fixture's class belongs to: one per (group, start) for a group's, its own id for an
     * individual class. A convention of the test — storage has a column for it — and it has to be stated
     * because that column is the whole of what makes classes one lesson (`GroupRules.sessions`).
     */
    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    @Test
    fun `a class naming a group is a group lesson, and one that doesn't is individual`() {
        assertEquals(LessonKind.GROUP, LessonKind.of(klass(1, groupId = 7)))
        assertEquals(LessonKind.INDIVIDUAL, LessonKind.of(klass(1, groupId = null)))
    }

    /**
     * The kind is read off the **class**, not off the session it is in.
     *
     * `ClassSession.of` used to drop the group id, because it doubled as the operand for changing one
     * member of a group lesson — so a session of one was a lie about what the lesson was, and reading the
     * kind from it called a group lesson individual. Attendance is that correction now, so a session of
     * one is simply an individual lesson and keeps whatever group it belongs to; the kind still comes off
     * the class, which is the level the fact lives at.
     */
    @Test
    fun `the kind of a group lesson's class is group, whether or not a session names it`() {
        val member = klass(3, groupId = 7)

        assertEquals(LessonKind.GROUP, LessonKind.of(member))
        assertEquals(LessonKind.GROUP, LessonKind.of(ClassSession.of(member).first))
    }

    @Test
    fun `a tally counts each kind, and omits a kind with none`() {
        val classes = listOf(klass(1), klass(2, groupId = 7), klass(3, groupId = 7))

        assertEquals(
            mapOf(LessonKind.INDIVIDUAL to 1, LessonKind.GROUP to 2),
            LessonKind.tally(classes),
        )
        assertEquals(mapOf(LessonKind.INDIVIDUAL to 1), LessonKind.tally(listOf(klass(1))))
        assertEquals(emptyMap<LessonKind, Int>(), LessonKind.tally(emptyList()))
    }

    @Test
    fun `ALL accepts both kinds and each other filter only its own`() {
        assertTrue(LessonFilter.ALL.accepts(LessonKind.INDIVIDUAL))
        assertTrue(LessonFilter.ALL.accepts(LessonKind.GROUP))

        assertTrue(LessonFilter.INDIVIDUAL.accepts(LessonKind.INDIVIDUAL))
        assertFalse(LessonFilter.INDIVIDUAL.accepts(LessonKind.GROUP))

        assertTrue(LessonFilter.GROUP.accepts(LessonKind.GROUP))
        assertFalse(LessonFilter.GROUP.accepts(LessonKind.INDIVIDUAL))
    }

    @Test
    fun `applying a filter keeps the list's order and drops only the other kind`() {
        val classes = listOf(klass(1), klass(2, groupId = 7), klass(3), klass(4, groupId = 8))

        assertEquals(classes, LessonFilter.ALL.apply(classes))
        assertEquals(listOf(1L, 3L), LessonFilter.INDIVIDUAL.apply(classes).map { it.id })
        assertEquals(listOf(2L, 4L), LessonFilter.GROUP.apply(classes).map { it.id })
    }

    @Test
    fun `every kind has a filter that selects exactly it`() {
        LessonKind.entries.forEach { kind ->
            val filter = LessonFilter.of(kind)
            assertTrue("$filter must accept $kind", filter.accepts(kind))
            LessonKind.entries.filter { it != kind }.forEach { other ->
                assertFalse("$filter must reject $other", filter.accepts(other))
            }
        }
    }

    /**
     * `accepted` is the form the shipped path can actually use — a student's lesson list is rows that
     * already know their kind, not raw classes, which is why the `List<TutoringClass>` overload sat
     * with no production caller while `StudentDetailScreen` spelled the same rule itself.
     */
    @Test
    fun `accepted filters anything that can say its own kind`() {
        data class Row(val id: Long, val kind: LessonKind)
        val rows = listOf(
            Row(1, LessonKind.INDIVIDUAL),
            Row(2, LessonKind.GROUP),
            Row(3, LessonKind.INDIVIDUAL),
        )

        assertEquals(listOf(1L, 2L, 3L), LessonFilter.ALL.accepted(rows) { it.kind }.map { it.id })
        assertEquals(listOf(1L, 3L), LessonFilter.INDIVIDUAL.accepted(rows) { it.kind }.map { it.id })
        assertEquals(listOf(2L), LessonFilter.GROUP.accepted(rows) { it.kind }.map { it.id })
    }

    @Test
    fun `the class-list form agrees with it`() {
        val individual = TutoringClass(id = 1, studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60)
        val group = individual.copy(id = 2, groupId = 7)
        val classes = listOf(individual, group)

        assertEquals(
            LessonFilter.GROUP.accepted(classes) { LessonKind.of(it) },
            LessonFilter.GROUP.apply(classes),
        )
    }
}
