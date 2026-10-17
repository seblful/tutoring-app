package com.seblful.klas.data.repository

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ClassReservationRaceTest {
    @get:Rule
    val env = TestEnv()

    private val target = Instant.parse("2026-06-02T10:00:00Z")
    private val rate = Money.ofMajor(30)

    @Test
    fun `a competing booking is checked after acquiring the transaction`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        compete(subject, student) { it.book(subject, target, 60, ClassMode.IN_PERSON, listOf(student), rate) }
    }

    @Test
    fun `a competing move is checked after acquiring the transaction`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), target.minusSeconds(7200)).single()
        val session = env.classRepo.getSessionFor(id)!!
        compete(subject, student) { it.move(session, target, target.minusSeconds(86400)) }
    }

    @Test
    fun `a competing edit is checked after acquiring the transaction`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), target.minusSeconds(7200)).single()
        val session = env.classRepo.getSessionFor(id)!!
        compete(subject, student) {
            it.apply(session, SessionEdit(subject, 60, ClassMode.IN_PERSON, rate), target, target.minusSeconds(86400))
        }
    }

    @Test
    fun `restoring a cancelled lesson cannot take an hour booked by a competing writer`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.cancelledLesson(subject, listOf(student), target).single()
        val session = env.classRepo.getSessionFor(id)!!
        compete(subject, student) { it.setStatus(session, LessonStatus.SCHEDULED) }
    }

    private suspend fun compete(
        subject: Long,
        student: Long,
        write: suspend (ClassRepository) -> LessonWrite<*>,
    ) = coroutineScope {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gate = object : Transactor {
            override suspend fun <T> transaction(block: suspend () -> T): T {
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
            env.fixtures.lesson(subject, listOf(student), target)
        } finally {
            release.complete(Unit)
        }
        assertTrue("The second reservation must be refused", pending.await() is LessonWrite.HourTaken)
    }
}
