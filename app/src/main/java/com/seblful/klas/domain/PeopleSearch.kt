package com.seblful.klas.domain

import java.util.Locale

/**
 * Matching a typed query against a name — students, groups, subjects alike.
 *
 * Here rather than in each list's view model because all three lists have to agree: searching «ан»
 * should find «Аня» on one screen and «Английский» on another by the same rule, and three
 * `contains(ignoreCase = true)` calls written a month apart is how they stop agreeing.
 *
 * The rules are deliberately small — this filters a list the tutor can already see, not a search
 * engine over a corpus:
 *
 * - **Substring, not prefix.** «пет» finds «Иван Петров»: the app stores one name field, and a tutor
 *   who writes «Иван Петров» is as likely to search by surname as by given name.
 * - **Case-insensitive in the Russian locale.** `lowercase()` without one uses the *default* locale,
 *   and Turkish famously maps `I` to `ı` — which would quietly break a Cyrillic app on a Turkish
 *   phone. The app is Russian-only by construction (see `RussianLocale`), so the locale is stated.
 * - **Whitespace-trimmed.** A trailing space from a keyboard's autocomplete must not empty the list.
 * - **Every term must match, in any order.** «пет ив» finds «Иван Петров». Typing a second word to
 *   narrow a result is the reflex the box invites, and demanding one contiguous substring punishes it.
 */
object PeopleSearch {

    private val RU = Locale.forLanguageTag("ru")

    /** Whether [name] satisfies [query]. A blank query matches everything — nothing is being asked. */
    fun matches(name: String, query: String): Boolean {
        val terms = query.lowercase(RU).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = name.lowercase(RU)
        return terms.all { haystack.contains(it) }
    }

    /** [matches] over a list, keeping its order. */
    fun <T> filter(items: List<T>, query: String, nameOf: (T) -> String): List<T> =
        if (query.isBlank()) items else items.filter { matches(nameOf(it), query) }
}
