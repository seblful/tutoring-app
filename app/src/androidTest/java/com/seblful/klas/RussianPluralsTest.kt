package com.seblful.klas

import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.format.inLanguage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Counted nouns have to decline. Russian picks between three forms by the number in front of them, and
 * the strings already carry all three — but the *rule* that chooses between them comes from the
 * configuration's locale, not from the folder the strings live in. Unpinned, an English phone applied
 * English rules to Russian text and the app said «0 занятия» and «5 занятия»; 1 and 2 came out right
 * only because English `one`/`other` happen to coincide with Russian `one`/`few` there, which is exactly
 * what made the bug easy to miss.
 *
 * So these assert the whole rule set through the resources, not one form: the boundaries are what break.
 * Read through [inLanguage], which is where the fix lives — and which is now asked for *per language*
 * rather than pinned once, the app having two. `EnglishPluralsTest` is the other side of the same coin:
 * the mistake is symmetric, and an English phone reading Russian rules is the same defect as the reverse.
 */
class RussianPluralsTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext
        .inLanguage(java.util.Locale.forLanguageTag("ru"))
        .resources

    @Test
    fun aLessonCountTakesTheFormItsNumberCallsFor() {
        assertForms(
            R.plurals.lessons_count,
            0 to "0 занятий",
            1 to "1 занятие",
            2 to "2 занятия",
            4 to "4 занятия",
            // 5 through 20 are all the "many" form — the run English rules collapse into one.
            5 to "5 занятий",
            11 to "11 занятий",
            20 to "20 занятий",
            // ...and then it starts over, except for the teens inside every hundred.
            21 to "21 занятие",
            22 to "22 занятия",
            25 to "25 занятий",
            101 to "101 занятие",
            111 to "111 занятий",
        )
    }

    /** A second noun, so what is pinned is the rule set rather than one string's three items. */
    @Test
    fun anAttendanceCountTakesTheFormItsNumberCallsFor() {
        assertForms(
            R.plurals.group_lesson_attended,
            1 to "1 участник",
            3 to "3 участника",
            7 to "7 участников",
            11 to "11 участников",
            21 to "21 участник",
        )
    }

    /** Reports every count that came out wrong: which boundary broke is the useful part of a failure. */
    private fun assertForms(plural: Int, vararg cases: Pair<Int, String>) {
        val wrong = cases.mapNotNull { (count, expected) ->
            val actual = resources.getQuantityString(plural, count, count)
            "expected «$expected», got «$actual»".takeIf { actual != expected }
        }
        assertTrue(wrong.joinToString(prefix = "\n", separator = "\n"), wrong.isEmpty())
    }
}
