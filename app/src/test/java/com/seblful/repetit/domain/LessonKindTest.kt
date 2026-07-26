package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.TutoringClass
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
        status: ClassStatus = ClassStatus.COMPLETED,
        charge: Money = Money.ofMajor(30),
    ) = TutoringClass(
        id = id,
        studentId = id,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        status = status,
        charge = if (status.isBillable) charge else Money.ZERO,
        groupId = groupId,
    )

    @Test
    fun `a class naming a group is a group lesson, and one that doesn't is individual`() {
        assertEquals(LessonKind.GROUP, LessonKind.of(klass(1, groupId = 7)))
        assertEquals(LessonKind.INDIVIDUAL, LessonKind.of(klass(1, groupId = null)))
    }

    /**
     * `ClassSession.of` makes a session of one out of a single member's class so that "this one didn't
     * come" stays expressible, and such a session deliberately carries **no** group id. Reading the
     * kind off the session there would call a group lesson individual, which is why the kind reads the
     * class instead.
     */
    @Test
    fun `one member's class of a group lesson is still a group lesson`() {
        val member = klass(3, groupId = 7)
        val asSessionOfOne = ClassSession.of(member)

        assertFalse("a session of one carries no group id", asSessionOfOne.isGroup)
        assertEquals(LessonKind.GROUP, LessonKind.of(asSessionOfOne.first))
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
}
