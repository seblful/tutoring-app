package com.seblful.klas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    /** Substring, not prefix: the app stores one name field, and surnames are searched for too. */
    @Test
    fun `a surname is found inside a full name`() {
        assertTrue(PeopleSearch.matches("Иван Петров", "петров"))
        assertTrue(PeopleSearch.matches("Иван Петров", "етр"))
    }

    /**
     * Typing a second word to narrow a result is the reflex a search box invites, and demanding one
     * contiguous substring would punish it: «пет ив» has to find «Иван Петров».
     */
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
