package com.seblful.klas.data.repository

import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositorySeriesTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()
    private val repo = env.classRepo

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val firstDay: LocalDate = LocalDate.parse("2026-09-07")
    private val atFour: LocalTime = LocalTime(16, 0)
    private val rate = Money.ofMajor(30)

    /** The subject and the two students the writes below name by id — the database refuses phantoms. */
    @BeforeTest
    fun seed() = runTest {
        check(env.fixtures.subject() == SUBJECT)
        check(env.fixtures.student() == STUDENT)
        check(env.fixtures.student("Боря") == OTHER)
    }

    private fun weekly(count: Int): List<Instant> =
        Recurrence.startsFor(firstDay, atFour, Repeat.WEEKLY, count, zone)

    private suspend fun bookSeries(
        starts: List<Instant>,
        studentIds: Collection<Long> = listOf(STUDENT),
        groupId: Long? = null,
    ) = repo.bookSeries(
        subjectId = SUBJECT,
        starts = starts,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        studentIds = studentIds,
        price = rate,
        groupId = groupId,
    )

    @Test
    fun `a term of lessons is written - one lesson per occurrence`() = runTest {
        val written = bookSeries(weekly(4))

        assertEquals(4, written.lessons)
        assertEquals(4, written.bookedIds.size)
        assertTrue(written.skipped.isEmpty(), "nothing stood in the way of an empty schedule")
        assertEquals(4, SessionRules.sessions(repo.getAll()).size)
    }

    @Test
    fun `each lesson lands on its own occurrence - at the hour that was picked`() = runTest {
        val starts = weekly(4)
        bookSeries(starts)

        assertEquals(starts, repo.getAll().map { it.start }.sorted())
        repo.getAll().forEach { assertEquals(atFour, it.start.toLocalDateTime(zone).time) }
    }

    @Test
    fun `every lesson of a run is scheduled - and billed at the rate the form carried`() = runTest {
        bookSeries(weekly(3))

        repo.getAll().forEach {
            assertEquals(LessonStatus.SCHEDULED, it.status)
            assertEquals(rate, it.price)
        }
    }

    @Test
    fun `the lessons of a run are separate lessons - not one lesson repeated`() = runTest {
        bookSeries(weekly(4))
        assertEquals(4, repo.getAll().map { it.lessonId }.distinct().size)
    }

    @Test
    fun `a single-occurrence run is an ordinary booking`() = runTest {
        val written = bookSeries(weekly(1))

        assertEquals(1, written.lessons)
        assertTrue(written.skipped.isEmpty())
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun `a whole term goes in as one transaction`() = runTest {
        val before = env.transactor.count
        bookSeries(weekly(12))

        assertEquals(1, env.transactor.count - before, "twelve lessons, one transaction")
        assertEquals(1, env.transactor.maxDepth, "and it is flat")
    }

    @Test
    fun `a term re-derives the reminder plan once - not once per lesson`() = runTest {
        bookSeries(weekly(12))
        assertEquals(1, env.reminders.refreshCount)
    }

    @Test
    fun `every lesson of a term is mirrored to the calendar`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        bookSeries(weekly(5))

        assertEquals(5, env.calendar.upserts.size)
        assertTrue(
            repo.getAll().all {
                it.calendarEventId != null
            },
            "each lesson keeps the event id its mirror returned",
        )
    }

    @Test
    fun `an occurrence whose hour is taken is skipped - and the rest are booked`() = runTest {
        val starts = weekly(4)
        repo.book(
            subjectId = SUBJECT,
            start = starts[2],
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(OTHER),
            price = rate,
        ).written()

        val written = bookSeries(starts)

        assertEquals(3, written.lessons)
        assertEquals(listOf(starts[2]), written.skipped.map { it.start })
    }

    @Test
    fun `a skipped occurrence names the lesson that holds the hour`() = runTest {
        val starts = weekly(2)
        val inTheWay = repo.book(
            subjectId = SUBJECT,
            start = starts[1],
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(OTHER),
            price = rate,
        ).written().single()

        val skipped = bookSeries(starts).skipped.single()

        assertEquals(inTheWay, skipped.clash.first.id)
    }

    @Test
    fun `a term whose every hour is taken writes nothing at all`() = runTest {
        val starts = weekly(3)
        starts.forEach { at ->
            repo.book(
                subjectId = SUBJECT,
                start = at,
                durationMinutes = 60,
                mode = ClassMode.IN_PERSON,
                studentIds = listOf(OTHER),
                price = rate,
            ).written()
        }
        val refreshesBefore = env.reminders.refreshCount
        val transactionsBefore = env.transactor.count

        val written = bookSeries(starts)

        assertTrue(written.wroteNothing)
        assertEquals(0, written.bookedIds.size)
        assertEquals(3, written.skipped.size)
        assertEquals(refreshesBefore, env.reminders.refreshCount)
        assertEquals(transactionsBefore + 1, env.transactor.count, "one transaction checks the refusal")
    }

    @Test
    fun `a cancelled lesson gives its hour back to a run`() = runTest {
        val starts = weekly(2)
        val cancelled = repo.book(
            subjectId = SUBJECT,
            start = starts[1],
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(OTHER),
            price = rate,
        ).written().single()
        repo.setStatus(repo.getSessionFor(cancelled)!!, LessonStatus.CANCELLED)

        val written = bookSeries(starts)

        assertEquals(2, written.lessons, "a called-off lesson is not in the way")
        assertTrue(written.skipped.isEmpty())
    }

    @Test
    fun `occurrences of one run never stand in each other's way`() = runTest {
        // They are a week apart and a lesson is at most 23 h 59 m, which is what lets the check run
        // against the schedule as it stands rather than against rows not yet committed.
        val written = repo.bookSeries(
            subjectId = SUBJECT,
            starts = weekly(6),
            durationMinutes = 23 * 60 + 59,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(STUDENT),
            price = rate,
        )

        assertEquals(6, written.lessons)
        assertTrue(written.skipped.isEmpty())
    }

    // --- Groups ----------------------------------------------------------------------------------

    @Test
    fun `a group's term writes a class per member per occurrence - and counts lessons`() = runTest {
        val ann = env.fixtures.student("Аня")
        val bob = env.fixtures.student("Борис")
        val group = env.fixtures.group(members = setOf(ann, bob), rate = rate)

        val written = bookSeries(weekly(4), studentIds = listOf(ann, bob), groupId = group)

        assertEquals(4, written.lessons)
        assertEquals(8, written.bookedIds.size)
        assertEquals(4, SessionRules.sessions(repo.getAll()).size)
    }

    @Test
    fun `a run with no occurrences is a caller's bug - not an empty booking`() = runTest {
        // Returning "booked nothing, skipped nothing" would report success for a save that wrote no rows.
        val failure = runCatching { bookSeries(emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "expected a refusal, got $failure")
    }

    @Test
    fun `a run for nobody is refused`() = runTest {
        val failure = runCatching { bookSeries(weekly(4), studentIds = emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "expected a refusal, got $failure")
    }

    private companion object {
        const val STUDENT = 1L
        const val OTHER = 2L
        const val SUBJECT = 1L
    }
}
