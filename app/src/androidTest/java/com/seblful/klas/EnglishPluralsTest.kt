package com.seblful.klas

import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.format.inLanguage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class EnglishPluralsTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext
        .inLanguage(Locale.forLanguageTag("en"))
        .resources

    @Test
    fun aLessonCountIsSingularOnlyAtOne() {
        assertForms(
            R.plurals.lessons_count,
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
            R.plurals.group_lesson_attended,
            1 to "1 member",
            3 to "3 members",
            21 to "21 members",
        )
    }

    private fun assertForms(plural: Int, vararg cases: Pair<Int, String>) {
        val wrong = cases.mapNotNull { (count, expected) ->
            val actual = resources.getQuantityString(plural, count, count)
            "expected «$expected», got «$actual»".takeIf { actual != expected }
        }
        assertTrue(wrong.joinToString(prefix = "\n", separator = "\n"), wrong.isEmpty())
    }
}
