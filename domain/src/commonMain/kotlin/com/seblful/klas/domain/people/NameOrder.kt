package com.seblful.klas.domain.people

/**
 * The order names are listed in, everywhere the app lists them.
 *
 * Names are ordered here, in Kotlin, not in SQL. SQLite's built-in `NOCASE` folds only the 26 ASCII
 * letters, so in a Russian-language app it does not fold case at all: «Ярослав» would sort *before*
 * «анна», because every capital Cyrillic letter has a lower code point than every small one. And an
 * ordering in the query would have to be restated in the in-memory fake DAOs the JVM tests run against —
 * where `lowercase()` *does* fold Cyrillic — so the tests would assert an order the app never produced.
 *
 * Done once here, the app and its tests run the same code, and Cyrillic names sort the way a reader
 * expects. Time and id ordering stays in SQL, where no collation is involved.
 */
object NameOrder {

    /**
     * Case-insensitive by name, with ties broken by the raw name so the order is total and stable
     * («Аня» and «аня» never swap between emissions).
     *
     * Deliberately `lowercase` rather than a locale [java.text.Collator]: it is Unicode-aware, which is the
     * part that matters here, and it gives the same answer on a device and on the JVM — which a platform
     * collator does not guarantee.
     *
     * **In [java.util.Locale.ROOT], stated rather than left to the default**, which is the phone's: Turkish
     * maps `I` to `ı`, so a tutor with a Turkish phone would get their Latin-named students ordered
     * differently from everyone else's. Plain case folding is what is wanted, and it belongs to no language —
     * the same answer, for the same reason, as `domain/people/PeopleSearch`, so what a name *matches* and where it
     * *sorts* cannot disagree.
     */
    val comparator: Comparator<String> =
        compareBy({ name: String -> name.lowercase() }, { it })

    /** [items] ordered by their display [name]. */
    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> =
        items.sortedWith(by(name))

    /**
     * This order, over whatever carries a [name] — a row, a pairing, a domain object.
     *
     * Exists so that a caller doing more than a plain sort can still get the order from here instead of
     * restating it: `sortedBy { it.name.lowercase() }` loses the tie-break that keeps «Аня» and «аня» from
     * swapping between emissions, and `sortedBy { it.name }` does not case-fold at all — the
     * `COLLATE NOCASE` behaviour again, sorting «Ярослав» before «анна». Compose it as a tie-break with
     * [java.util.Comparator.thenComparing] or Kotlin's `then`, which is what the students list needs
     * behind its debt ordering.
     */
    fun <T> by(name: (T) -> String): Comparator<T> =
        Comparator { a, b -> comparator.compare(name(a), name(b)) }

    /**
     * [items] with the archived ones last, each group ordered by [name]. Archiving is how somebody
     * leaves without erasing history, so they stay listed — just out of the way.
     */
    fun <T> sortedArchivedLast(items: List<T>, archived: (T) -> Boolean, name: (T) -> String): List<T> =
        items.sortedWith(
            compareBy<T> { archived(it) }.thenComparator { a, b -> comparator.compare(name(a), name(b)) },
        )
}
