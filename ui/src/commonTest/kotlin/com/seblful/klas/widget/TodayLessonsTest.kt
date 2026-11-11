package com.seblful.klas.widget

import com.seblful.klas.text.Texts
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TodayLessonsTest {

    @Test
    fun `a row reads start to end then subject - and only the times when the subject has no name`() {
        val lesson = TodayLesson(7, nine, nine + 1.hours, "Аня", "Алгебра", taught = false, canMarkTaught = true)

        assertEquals("09:00–10:00 · Алгебра", lesson.toRow(false, russian, nine, texts).supportingText)
        assertEquals("09:00–10:00", lesson.copy(subject = "").toRow(false, russian, nine, texts).supportingText)
    }

    @Test
    fun `a lesson is on now from its start until its end or until it is marked - and its tick names it`() {
        val lesson = TodayLesson(7, nine, nine + 1.hours, "Аня", "Алгебра", taught = false, canMarkTaught = true)

        assertFalse(lesson.toRow(false, russian, nine - 1.minutes, texts).onNow)
        assertTrue(lesson.toRow(false, russian, nine + 30.minutes, texts).onNow)
        assertFalse(lesson.toRow(false, russian, nine + 1.hours, texts).onNow)
        assertFalse(lesson.copy(taught = true).toRow(false, russian, nine + 30.minutes, texts).onNow)
        assertEquals("Mark Аня, 09:00 taught", lesson.toRow(false, russian, nine, texts).tickLabel)
    }

    @Test
    fun `the widget next redraws at the next start or end - and at none once the day is over`() {
        val first = TodayLesson(1, nine, nine + 1.hours, "Аня", "", taught = false, canMarkTaught = true)
        val second = TodayLesson(2, nine + 2.hours, nine + 3.hours, "Иван", "", taught = false, canMarkTaught = true)

        assertEquals(nine, TodayLessons.nextChange(listOf(first, second), nine - 1.hours))
        assertEquals(nine + 1.hours, TodayLessons.nextChange(listOf(first, second), nine))
        assertEquals(nine + 2.hours, TodayLessons.nextChange(listOf(first, second), nine + 90.minutes))
        assertNull(TodayLessons.nextChange(listOf(first, second), nine + 3.hours))
    }

    // Times are shown in the phone's zone, whatever the build machine's is.
    private val nine = LocalDateTime(2026, 6, 1, 9, 0).toInstant(TimeZone.currentSystemDefault())
    private val russian = "ru"
    private val texts = TodayTexts(
        "Today", "", "", "Mark as taught", "Taught", "On now",
        markLesson = "Mark %1${'$'}s, %2${'$'}s taught", lessonTaught = "%1${'$'}s, %2${'$'}s: taught",
    )

    @Test
    fun `the picker's sample day is in the app's language with its first lesson taught`() {
        val russian = "ru"
        val rows = Texts.of("ru").sampleRows(hour12 = false, russian)

        assertEquals(listOf("Аня Соколова", "11 «А»", "Иван Петров"), rows.map { it.title })
        assertEquals("09:00–10:00 · Алгебра", rows.first().supportingText)
        assertEquals(listOf(true, false, false), rows.map { it.taught })
        assertEquals(listOf(false, true, true), rows.map { it.canMarkTaught })
    }
}
