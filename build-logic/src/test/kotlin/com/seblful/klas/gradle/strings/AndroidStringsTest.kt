package com.seblful.klas.gradle.strings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidStringsTest {

    private fun decode(raw: String) = AndroidStrings.decode(raw, "test")

    @Test
    fun escapesBecomeTheirCharacters() {
        assertEquals("It's \"here\"\n\tback\\slash @?", decode("""It\'s \"here\"\n\tback\\slash \@\?"""))
        // As written in the XML: backslash, u, four hex digits.
        assertEquals("a b⁠c", decode("a\\u00A0b\\u2060c"))
    }

    @Test
    fun whitespaceCollapsesAndTrimsOutsideQuotes() {
        assertEquals("one two three", decode("  one \n   two\tthree  "))
        assertEquals("  kept  ", decode("\"  kept  \""))
        assertEquals("a  b", decode("a\" \" b"))
    }

    @Test
    fun aNoBreakSpaceInTheTextIsKept() {
        assertEquals("1 000  x", decode("1 000  x ").dropLast(1))
        assertEquals("x ", decode("x "))
    }

    @Test
    fun anEscapedSpaceIsNotTrimmed() {
        assertEquals("end ", decode("end\\u0020"))
    }

    @Test
    fun aBareApostropheIsRefusedAsAapt2RefusesIt() {
        assertFailsWith<IllegalStateException> { decode("it's") }
        assertEquals("it's", decode("\"it's\""))
    }

    @Test
    fun parsesStringsPluralsAndArraysInOrder() {
        val file = AndroidStrings.parse(
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <!-- comment -->
                <string name="b">B &amp; C</string>
                <string name="a" translatable="false">A</string>
                <plurals name="lessons">
                    <item quantity="one">%1${'$'}d lesson</item>
                    <item quantity="other">%1${'$'}d lessons</item>
                </plurals>
                <string-array name="days"><item>Mon</item><item>Tue</item></string-array>
            </resources>
            """.trimIndent(),
            "values/strings.xml",
        )
        assertEquals(listOf("b", "a"), file.strings.keys.toList())
        assertEquals("B & C", file.strings["b"])
        assertEquals(mapOf("one" to "%1\$d lesson", "other" to "%1\$d lessons"), file.plurals["lessons"])
        assertEquals(listOf("Mon", "Tue"), file.arrays["days"])
    }

    @Test
    fun aPluralNeedsOther() {
        val error = assertFailsWith<IllegalArgumentException> {
            AndroidStrings.parse(
                "<resources><plurals name=\"p\"><item quantity=\"one\">x</item></plurals></resources>",
                "f",
            )
        }
        assertTrue("other" in error.message.orEmpty())
    }

    @Test
    fun markupInTextIsRefused() {
        assertFailsWith<IllegalStateException> {
            AndroidStrings.parse("<resources><string name=\"s\">a <b>b</b></string></resources>", "f")
        }
    }
}
