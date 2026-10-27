package com.seblful.klas.ui.groups

import com.seblful.klas.domain.model.Student
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberSearchTest {
    private val roster = listOf(Student(id = 1, name = "Ava Thompson"), Student(id = 2, name = "Лукас Рид"))

    @Test
    fun `a roster is narrowed by any part of a name, in any case`() {
        assertEquals(listOf(1L), roster.matching("thom").map { it.id })
        assertEquals(listOf(2L), roster.matching(" рид ").map { it.id })
    }

    @Test
    fun `the words of a name may be typed in any order, as People's search takes them`() {
        assertEquals(listOf(1L), roster.matching("thompson ava").map { it.id })
    }

    @Test
    fun `an empty search keeps everybody`() {
        assertEquals(roster, roster.matching("  "))
    }
}
