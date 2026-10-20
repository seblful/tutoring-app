package com.seblful.klas.domain.people

import kotlin.test.Test
import kotlin.test.assertEquals

class NameOrderTest {

    private fun sorted(vararg names: String) = NameOrder.sorted(names.toList()) { it }

    @Test
    fun `Cyrillic names are ordered case-insensitively`() {
        assertEquals(listOf("анна", "Ярослав"), sorted("Ярослав", "анна"))
    }

    @Test
    fun `mixed-case spellings of one name sort together`() {
        assertEquals(
            listOf("Аня", "аня", "Борис"),
            sorted("Борис", "Аня", "аня"),
        )
    }

    @Test
    fun `ties are broken by the raw name, so the order never wobbles`() {
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
