package com.seblful.klas

import com.seblful.klas.testutil.texts
import com.seblful.klas.text.PluralResource
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Texts
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnglishPluralsTest {

    private val texts = Texts.of("en")

    @Test
    fun aLessonCountIsSingularOnlyAtOne() {
        assertForms(
            Plurals.lessons_count,
            0 to "0 lessons",
            1 to "1 lesson",
            2 to "2 lessons",
            5 to "5 lessons",
            11 to "11 lessons",
            21 to "21 lessons",
            101 to "101 lessons",
            111 to "111 lessons",
        )
    }

    @Test
    fun anAttendanceCountIsSingularOnlyAtOne() {
        assertForms(
            Plurals.group_lesson_attended,
            1 to "1 member",
            3 to "3 members",
            21 to "21 members",
        )
    }

    private fun assertForms(plural: PluralResource, vararg cases: Pair<Int, String>) {
        val wrong = cases.mapNotNull { (count, expected) ->
            val actual = texts.plural(plural, count, count)
            "expected «$expected», got «$actual»".takeIf { actual != expected }
        }
        assertTrue(wrong.joinToString(prefix = "\n", separator = "\n"), wrong.isEmpty())
    }
}
