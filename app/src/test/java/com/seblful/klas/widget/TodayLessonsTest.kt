package com.seblful.klas.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.inLanguage
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class TodayLessonsTest {

    @get:Rule
    val env = TestEnv()

    private val lessons = TodayLessons(env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.clock)

    /** The test clock's day in Minsk (UTC+3): 2026-06-01. */
    private fun today(localTime: String): Instant = Instant.parse("2026-06-01T$localTime:00+03:00")

    private suspend fun setStatus(classId: Long, status: LessonStatus) {
        env.classRepo.setStatus(env.classRepo.getSessionFor(classId)!!, status)
    }

    @Test
    fun `today's lessons in time order, a group lesson as one row titled by its group`() = runTest {
        val algebra = env.fixtures.subject("Алгебра")
        val anna = env.fixtures.student("Аня")
        val ivan = env.fixtures.student("Иван")
        val group = env.fixtures.group("11 «А»", subjectId = algebra, members = setOf(anna, ivan))
        val groupLesson = env.fixtures.lesson(algebra, listOf(anna, ivan), today("16:00"), groupId = group)
        val annasLesson = env.fixtures.lesson(algebra, listOf(anna), today("09:00")).single()

        val rows = lessons.observe().first()

        assertEquals(
            listOf(
                TodayLesson(
                    annasLesson, today("09:00"), today("10:00"), "Аня", "Алгебра", taught = false, canMarkTaught = true,
                ),
                TodayLesson(
                    groupLesson.first(), today("16:00"), today("17:00"), "11 «А»", "Алгебра",
                    taught = false, canMarkTaught = true,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a taught lesson stays checked, a cancelled one leaves, other days are not today`() = runTest {
        val algebra = env.fixtures.subject()
        val anna = env.fixtures.student()
        val taught = env.fixtures.lesson(algebra, listOf(anna), today("09:00")).single()
        val cancelled = env.fixtures.lesson(algebra, listOf(anna), today("11:00")).single()
        env.fixtures.lesson(algebra, listOf(anna), Instant.parse("2026-06-02T09:00:00+03:00"))
        env.fixtures.lesson(algebra, listOf(anna), Instant.parse("2026-05-31T23:00:00+03:00"))
        setStatus(taught, LessonStatus.COMPLETED)
        setStatus(cancelled, LessonStatus.CANCELLED)

        val rows = lessons.observe().first()

        assertEquals(listOf(taught), rows.map { it.classId })
        assertEquals(true, rows.single().taught)
        assertEquals(false, rows.single().canMarkTaught)
    }

    @Test
    fun `a row reads start to end then subject, and only the times when the subject has no name`() {
        val lesson = TodayLesson(7, nine, nine + 1.hours, "Аня", "Алгебра", taught = false, canMarkTaught = true)

        assertEquals("09:00–10:00 · Алгебра", lesson.toRow(false, russian, nine, texts).supportingText)
        assertEquals("09:00–10:00", lesson.copy(subject = "").toRow(false, russian, nine, texts).supportingText)
    }

    @Test
    fun `a lesson is on now from its start until its end or until it is marked, and its tick names it`() {
        val lesson = TodayLesson(7, nine, nine + 1.hours, "Аня", "Алгебра", taught = false, canMarkTaught = true)

        assertFalse(lesson.toRow(false, russian, nine - 1.minutes, texts).onNow)
        assertTrue(lesson.toRow(false, russian, nine + 30.minutes, texts).onNow)
        assertFalse(lesson.toRow(false, russian, nine + 1.hours, texts).onNow)
        assertFalse(lesson.copy(taught = true).toRow(false, russian, nine + 30.minutes, texts).onNow)
        assertEquals("Mark Аня, 09:00 taught", lesson.toRow(false, russian, nine, texts).tickLabel)
    }

    @Test
    fun `the widget next redraws at the next start or end, and at none once the day is over`() {
        val first = TodayLesson(1, nine, nine + 1.hours, "Аня", "", taught = false, canMarkTaught = true)
        val second = TodayLesson(2, nine + 2.hours, nine + 3.hours, "Иван", "", taught = false, canMarkTaught = true)

        assertEquals(nine, TodayLessons.nextChange(listOf(first, second), nine - 1.hours))
        assertEquals(nine + 1.hours, TodayLessons.nextChange(listOf(first, second), nine))
        assertEquals(nine + 2.hours, TodayLessons.nextChange(listOf(first, second), nine + 90.minutes))
        assertNull(TodayLessons.nextChange(listOf(first, second), nine + 3.hours))
    }

    // Times are shown in the phone's zone, whatever the build machine's is.
    private val nine = LocalDateTime(2026, 6, 1, 9, 0).toInstant(TimeZone.currentSystemDefault())
    private val russian = Locale.forLanguageTag("ru")
    private val texts = TodayTexts(
        "Today", "", "", "Mark as taught", "Taught", "On now",
        markLesson = "Mark %1${'$'}s, %2${'$'}s taught", lessonTaught = "%1${'$'}s, %2${'$'}s: taught",
    )

    @Test
    fun `the picker's sample day is in the app's language, its first lesson taught`() {
        val russian = Locale.forLanguageTag("ru")
        val rows = ApplicationProvider.getApplicationContext<Context>().inLanguage(russian)
            .sampleRows(hour12 = false, russian)

        assertEquals(listOf("Аня Соколова", "11 «А»", "Иван Петров"), rows.map { it.title })
        assertEquals("09:00–10:00 · Алгебра", rows.first().supportingText)
        assertEquals(listOf(true, false, false), rows.map { it.taught })
        assertEquals(listOf(false, true, true), rows.map { it.canMarkTaught })
    }
}
