package com.seblful.klas

import com.seblful.klas.text.PluralResource
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Texts
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RussianPluralsTest {

    private val texts = Texts.of("ru")

    @Test
    fun aLessonCountTakesTheFormItsNumberCallsFor() {
        assertForms(
            Plurals.lessons_count,
            0 to "0 занятий",
            1 to "1 занятие",
            2 to "2 занятия",
            4 to "4 занятия",
            5 to "5 занятий",
            11 to "11 занятий",
            20 to "20 занятий",
            21 to "21 занятие",
            22 to "22 занятия",
            25 to "25 занятий",
            101 to "101 занятие",
            111 to "111 занятий",
        )
    }

    @Test
    fun anAttendanceCountTakesTheFormItsNumberCallsFor() {
        assertForms(
            Plurals.group_lesson_attended,
            1 to "1 участник",
            3 to "3 участника",
            7 to "7 участников",
            11 to "11 участников",
            21 to "21 участник",
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
