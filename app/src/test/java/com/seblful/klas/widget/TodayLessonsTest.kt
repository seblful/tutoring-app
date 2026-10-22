package com.seblful.klas.widget

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
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
                TodayLesson(annasLesson, today("09:00"), "Аня", "Алгебра", taught = false, canMarkTaught = true),
                TodayLesson(
                    groupLesson.first(), today("16:00"), "11 «А»", "Алгебра", taught = false, canMarkTaught = true,
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
    fun `a row reads time then subject, and only the time when the subject has no name`() {
        // Times are shown in the phone's zone, whatever the build machine's is.
        val nine = LocalDateTime(2026, 6, 1, 9, 0).toInstant(TimeZone.currentSystemDefault())
        val lesson = TodayLesson(7, nine, "Аня", "Алгебра", taught = false, canMarkTaught = true)

        val russian = Locale.forLanguageTag("ru")
        assertEquals("09:00 · Алгебра", lesson.toRow(hour12 = false, russian).supportingText)
        assertEquals("09:00", lesson.copy(subject = "").toRow(hour12 = false, russian).supportingText)
    }
}
