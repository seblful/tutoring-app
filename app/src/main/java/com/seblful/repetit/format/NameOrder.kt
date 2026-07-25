package com.seblful.repetit.format

/**
 * The order names are listed in, everywhere the app lists them.
 *
 * This used to live in SQL as `ORDER BY name COLLATE NOCASE`, which was wrong twice over. SQLite's
 * built-in `NOCASE` folds only the 26 ASCII letters, so in a Russian-language app it does not fold
 * case at all: «Ярослав» sorted *before* «анна», because every capital Cyrillic letter has a lower
 * code point than every small one. And because the ordering lived in the query, the in-memory fake
 * DAOs the JVM tests run against had to restate it in Kotlin — where `lowercase()` *does* fold
 * Cyrillic — so the tests asserted an order the app never produced.
 *
 * Ordering by name is therefore done here, in Kotlin, once: the app and its tests run the same code,
 * and Cyrillic names sort the way a reader expects. Time and id ordering stays in SQL, where no
 * collation is involved.
 */
object NameOrder {

    /**
     * Case-insensitive by name, with ties broken by the raw name so the order is total and stable
     * («Аня» and «аня» never swap between emissions).
     *
     * Deliberately `lowercase()` rather than a locale [java.text.Collator]: it is Unicode-aware,
     * which is the part that matters here, and it gives the same answer on a device and on the JVM —
     * which a platform collator does not guarantee.
     */
    val comparator: Comparator<String> = compareBy({ it: String -> it.lowercase() }, { it })

    /** [items] ordered by their display [name]. */
    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> =
        items.sortedWith { a, b -> comparator.compare(name(a), name(b)) }

    /**
     * [items] with the archived ones last, each group ordered by [name]. Archiving is how somebody
     * leaves without erasing history, so they stay listed — just out of the way.
     */
    fun <T> sortedArchivedLast(items: List<T>, archived: (T) -> Boolean, name: (T) -> String): List<T> =
        items.sortedWith(
            compareBy<T> { archived(it) }.thenComparator { a, b -> comparator.compare(name(a), name(b)) },
        )
}
