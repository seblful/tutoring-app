package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class StaleSessionWriteTest {
    @get:Rule
    val env = TestEnv()

    private val start = Instant.parse("2026-06-02T10:00:00Z")
    private val afterwards = start.plus((86_400).seconds)
    private val rate = Money.ofMajor(30)

    @Test
    fun `a move does not un-complete a lesson completed after it was read`() = runTest {
        val (subject, student) = seed()
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val stale = env.classRepo.getSessionFor(id)!!

        interleave({ it.move(stale, start.plus((1_800).seconds), now = afterwards) }) {
            env.fixtures.setStatus(id, LessonStatus.COMPLETED)
        }

        val stored = env.classRepo.getById(id)!!
        assertEquals(LessonStatus.COMPLETED, stored.status)
        assertTrue("The completed lesson must stay billed", stored.charge > Money.ZERO)
    }

    @Test
    fun `an edit does not un-complete a lesson completed after it was read`() = runTest {
        val (subject, student) = seed()
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val stale = env.classRepo.getSessionFor(id)!!

        interleave({
            it.apply(stale, SessionEdit(subject, 60, ClassMode.IN_PERSON, Money.ofMajor(40)), start, now = afterwards)
        }) {
            env.fixtures.setStatus(id, LessonStatus.COMPLETED)
        }

        val stored = env.classRepo.getById(id)!!
        assertEquals(LessonStatus.COMPLETED, stored.status)
        assertEquals(Money.ofMajor(40), stored.charge)
    }

    @Test
    fun `a status change to a lesson deleted after it was read writes nothing`() = runTest {
        val (subject, student) = seed()
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val stale = env.classRepo.getSessionFor(id)!!

        val written = interleave({ it.setStatus(stale, LessonStatus.COMPLETED) }) {
            env.classRepo.delete(stale)
        }

        assertSame(LessonWrite.Gone, written)
        assertNull(env.classRepo.getById(id))
    }

    @Test
    fun `auto-completion does not bill a lesson cancelled after the pass found it due`() = runTest {
        val (subject, student) = seed()
        val id = env.fixtures.lesson(subject, listOf(student), start).single()

        interleave({ it.autoCompleteDue(afterwards) }) {
            env.fixtures.setStatus(id, LessonStatus.CANCELLED)
        }

        val stored = env.classRepo.getById(id)!!
        assertEquals(LessonStatus.CANCELLED, stored.status)
        assertEquals(Money.ZERO, stored.charge)
    }

    @Test
    fun `archiving does not cancel a lesson completed after its classes were found`() = runTest {
        val (subject, student) = seed()
        val id = env.fixtures.lesson(subject, listOf(student), start).single()

        interleave({ it.cancelScheduledFor(student) }) {
            env.fixtures.setStatus(id, LessonStatus.COMPLETED)
        }

        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(id)!!.status)
    }

    private suspend fun seed(): Pair<Long, Long> {
        val subject = env.fixtures.subject()
        return subject to env.fixtures.pricedStudent(subjectId = subject, rate = rate)
    }

    private suspend fun <T> interleave(
        write: suspend (ClassRepository) -> T,
        meanwhile: suspend () -> Unit,
    ): T = coroutineScope {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gate = object : Transactor {
            override suspend fun <R> transaction(block: suspend () -> R): R {
                entered.complete(Unit)
                release.await()
                return env.transactor.transaction(block)
            }
        }
        val repository = ClassRepository(
            env.classDao, env.lessonDao, env.attendanceDao, env.calendarMirror, env.reminders, gate,
        )
        val pending = async { write(repository) }
        entered.await()
        try {
            meanwhile()
        } finally {
            release.complete(Unit)
        }
        pending.await()
    }
}
