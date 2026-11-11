package com.seblful.klas.gradle.strings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogWriterTest {

    private val english = StringFile(
        strings = linkedMapOf("title" to "Lessons", "count" to "%1\$d of %2\$s, 100%%"),
        plurals = linkedMapOf("lessons" to linkedMapOf("one" to "%1\$d lesson", "other" to "%1\$d lessons")),
        arrays = linkedMapOf("days" to listOf("Mon", "Tue")),
    )
    private val russian = StringFile(
        strings = linkedMapOf("title" to "Занятия"),
        plurals = linkedMapOf(
            "lessons" to linkedMapOf("one" to "%1\$d занятие", "few" to "%1\$d занятия", "many" to "%1\$d занятий", "other" to "%1\$d занятия"),
        ),
        arrays = emptyMap(),
    )

    private fun write(vararg languages: Pair<String, StringFile>) =
        CatalogWriter("com.example.text", "en").write(mapOf(*languages))

    @Test
    fun keysFollowTheDefaultFileAndMissingTranslationsAreNull() {
        val files = write("en" to english, "ru" to russian)
        val keys = files.getValue("TextKeys.kt")
        assertTrue("public val title: StringResource = StringResource(0, \"title\")" in keys)
        assertTrue("public val count: StringResource = StringResource(1, \"count\")" in keys)
        val ru = files.getValue("TextTableRu.kt")
        assertTrue("\"Занятия\",\n        null," in ru, ru)
        assertTrue("PluralText(one = \"%1\\\$d занятие\", few = \"%1\\\$d занятия\", many = \"%1\\\$d занятий\", other = \"%1\\\$d занятия\")" in ru, ru)
        assertTrue("override val arrays: Array<List<String>?> = arrayOf(\n        null," in ru, ru)
        val index = files.getValue("TextTables.kt")
        assertTrue("\"ru\" -> TextTableRu" in index)
        assertTrue("setOf(\"en\", \"ru\")" in index)
    }

    @Test
    fun aKeyOnlyATranslationHasIsRefused() {
        val stray = russian.copy(strings = russian.strings + ("extra" to "x"))
        assertFailsWith<IllegalArgumentException> { write("en" to english, "ru" to stray) }
    }

    @Test
    fun formatsOtherThanPositionalStringsAndIntegersAreRefused() {
        for (text in listOf("%s", "%d", "%.1f", "%1\$f", "50%")) {
            val file = english.copy(strings = linkedMapOf("title" to text))
            assertFailsWith<IllegalArgumentException>(text) { write("en" to file) }
        }
    }

    @Test
    fun literalsEscapeWhatKotlinWouldReadAndInvisibleCharacters() {
        assertEquals("\"a\\\"b\\\\c\\\$d\\ne\"", CatalogWriter.literal("a\"b\\c\$d\ne"))
        assertEquals("\"1\\u00a0000\\u2060₽\"", CatalogWriter.literal("1 000⁠₽"))
        assertEquals("null", CatalogWriter.literal(null))
    }

    @Test
    fun tableNamesAndFolderTags() {
        assertEquals("TextTablePtBr", CatalogWriter.tableName("pt-BR"))
        assertEquals("pt-BR", GenerateTextCatalog.tagOf("values-pt-rBR"))
        assertEquals("ru", GenerateTextCatalog.tagOf("values-ru"))
        assertNull(GenerateTextCatalog.tagOf("values"))
        assertFailsWith<IllegalArgumentException> { GenerateTextCatalog.tagOf("values-night") }
    }
}
