package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
