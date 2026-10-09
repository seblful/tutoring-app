package com.seblful.klas.format

/**
 * The order names are listed in, everywhere the app lists them.
 *
 * This used to live in SQL as `ORDER BY name COLLATE NOCASE`, which was wrong twice over. SQLite's
 * built-in `NOCASE` folds only the 26 ASCII letters, so in a Russian-language app it does not fold
 * case at all: «Ярослав» sorted *before* «анна», because every capital Cyrillic letter has a lower
 * code point than every small one. And because the ordering lived in the query, the in-memory fake
 * DAOs the JVM tests then ran against had to restate it in Kotlin — where `lowercase()` *does* fold
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
     * Deliberately `lowercase` rather than a locale [java.text.Collator]: it is Unicode-aware, which is the
     * part that matters here, and it gives the same answer on a device and on the JVM — which a platform
     * collator does not guarantee.
     *
     * **In [java.util.Locale.ROOT], stated rather than left to the default**, which is the phone's: Turkish
     * maps `I` to `ı`, so a tutor with a Turkish phone would have had their Latin-named students ordered
     * differently from everyone else's. Plain case folding is what is wanted, and it belongs to no language —
     * the same answer, for the same reason, as `domain/PeopleSearch`, so what a name *matches* and where it
     * *sorts* cannot disagree.
     */
    val comparator: Comparator<String> =
        compareBy({ it: String -> it.lowercase(java.util.Locale.ROOT) }, { it })

    /** [items] ordered by their display [name]. */
    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> =
        items.sortedWith(by(name))

    /**
     * This order, over whatever carries a [name] — a row, a pairing, a domain object.
     *
     * Exists so that a caller doing more than a plain sort can still get the order from here instead of
     * restating it. Three view models used to: two spelled it `sortedBy { it.name.lowercase() }`, losing
     * the tie-break that keeps «Аня» and «аня» from swapping between emissions, and the third
     * `sortedBy { it.name }`, which does not case-fold at all — the exact `COLLATE NOCASE` behaviour
     * this object was written to get rid of, back in a Russian-language app where it sorts «Ярослав»
     * before «анна». Compose it as a tie-break with [java.util.Comparator.thenComparing] or Kotlin's
     * `then`, which is what the students list needs behind its debt ordering.
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
