package com.seblful.klas.domain.people

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The order names are listed in. The cases that matter are Cyrillic: this app's UI is Russian, so
 * every subject, student and group name is, and the SQL collation this replaced could not fold their
 * case at all.
 */
class NameOrderTest {

    private fun sorted(vararg names: String) = NameOrder.sorted(names.toList()) { it }

    @Test
    fun `Cyrillic names are ordered case-insensitively`() {
        // The case SQLite's COLLATE NOCASE got wrong: it folds ASCII only, so every capital Cyrillic
        // letter sorted before every small one and «Ярослав» came before «анна».
        assertEquals(listOf("анна", "Ярослав"), sorted("Ярослав", "анна"))
    }

    @Test
    fun `mixed-case spellings of one name sort together`() {
        // The point is that both spellings of «аня» land before «Борис» — case doesn't scatter them.
        assertEquals(
            listOf("Аня", "аня", "Борис"),
            sorted("Борис", "Аня", "аня"),
        )
    }

    @Test
    fun `ties are broken by the raw name, so the order never wobbles`() {
        // Same lowercase key: the raw comparison decides — which spelling wins doesn't matter, only
        // that it is always the same one, whatever order the rows arrive in.
        assertEquals(listOf("Аня", "аня"), sorted("Аня", "аня"))
        assertEquals(listOf("Аня", "аня"), sorted("аня", "Аня"))
    }

    @Test
    fun `Latin names are ordered case-insensitively too`() {
        assertEquals(listOf("algebra", "Biology", "chemistry"), sorted("chemistry", "algebra", "Biology"))
    }

    @Test
    fun `archived entries go last, each group ordered by name`() {
        data class Row(val name: String, val archived: Boolean)

        val rows = listOf(
            Row("Ярослав", archived = false),
            Row("Аня", archived = true),
            Row("борис", archived = false),
            Row("аня", archived = false),
        )

        val ordered = NameOrder.sortedArchivedLast(rows, archived = { it.archived }, name = { it.name })

        assertEquals(listOf("аня", "борис", "Ярослав", "Аня"), ordered.map { it.name })
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<String>(), NameOrder.sorted(emptyList<String>()) { it })
    }
}
