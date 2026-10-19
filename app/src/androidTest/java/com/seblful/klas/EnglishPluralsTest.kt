package com.seblful.klas

import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.format.inLanguage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * The other side of `RussianPluralsTest`, and the reason the fix had to become a *parameter* rather than a
 * pinned constant.
 *
 * The mistake is symmetric. A quantity's form comes from the configuration's locale and not from the folder
 * the string was loaded from, so an app displaying English on a Russian phone selects by Russian rules: at 1
 * that is `one` and right by luck, at 2 it is `few` — which English does not declare, so the selector falls
 * through to `other` and it is right by luck again — and at 5 it is `many`, which English also lacks. The
 * cases where it *reads* correct are exactly the cases a developer checks, which is what makes this worth a
 * test rather than a careful moment.
 *
 * So the assertions here are English's own two forms at the boundary that matters — one, and everything else
 * — read through the same [inLanguage] the app uses.
 */
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
            // The Russian rule set would have said «one» here, this being 1 mod 10 and not a teen.
            111 to "111 lessons",
        )
    }

    /** A second noun, so what is pinned is the rule set rather than one string's two items. */
    @Test
    fun anAttendanceCountIsSingularOnlyAtOne() {
        assertForms(
            R.plurals.group_lesson_attended,
            1 to "1 member",
            3 to "3 members",
            21 to "21 members",
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
