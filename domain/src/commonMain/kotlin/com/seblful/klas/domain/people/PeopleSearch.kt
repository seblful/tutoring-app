package com.seblful.klas.domain.people

/**
 * Case-insensitive substring search shared by students, groups, and subjects. Every nonblank query term
 * must occur in the name; a blank query matches everything.
 */
object PeopleSearch {

    /** Whether [name] satisfies [query]. A blank query matches everything — nothing is being asked. */
    fun matches(name: String, query: String): Boolean {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = name.lowercase()
        return terms.all { haystack.contains(it) }
    }

    /** [matches] over a list, keeping its order. */
    fun <T> filter(items: List<T>, query: String, nameOf: (T) -> String): List<T> =
        if (query.isBlank()) items else items.filter { matches(nameOf(it), query) }
}
