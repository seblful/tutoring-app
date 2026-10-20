package com.seblful.klas.domain.people

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeopleSearchTest {

    @Test
    fun `a blank query matches everything`() {
        assertTrue(PeopleSearch.matches("Аня", ""))
        assertTrue(PeopleSearch.matches("Аня", "   "))
    }

    @Test
    fun `matching is case-insensitive in Cyrillic`() {
        assertTrue(PeopleSearch.matches("Аня", "аня"))
        assertTrue(PeopleSearch.matches("аня", "АНЯ"))
    }

    @Test
    fun `a surname is found inside a full name`() {
        assertTrue(PeopleSearch.matches("Иван Петров", "петров"))
        assertTrue(PeopleSearch.matches("Иван Петров", "етр"))
    }

    @Test
    fun `every term must match, in any order`() {
        assertTrue(PeopleSearch.matches("Иван Петров", "пет ив"))
        assertTrue(PeopleSearch.matches("Иван Петров", "ив пет"))
        assertFalse(PeopleSearch.matches("Иван Петров", "ив сид"))
    }

    @Test
    fun `surrounding whitespace does not empty the list`() {
        assertTrue(PeopleSearch.matches("Аня", "  аня  "))
    }

    @Test
    fun `nothing matches a query that is not in the name`() {
        assertFalse(PeopleSearch.matches("Аня", "зщ"))
    }

    @Test
    fun `filter keeps the order it was given`() {
        val names = listOf("Аня", "Борис", "Анна", "Вера")
        assertEquals(listOf("Аня", "Анна"), PeopleSearch.filter(names, "ан") { it })
        assertEquals(names, PeopleSearch.filter(names, "") { it })
    }
}
