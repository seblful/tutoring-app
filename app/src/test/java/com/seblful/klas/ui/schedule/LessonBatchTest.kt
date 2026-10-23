package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LessonBatchTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    private val batch get() = LessonBatch(env.classRepo, mainRule.appScope)

    /** Three lessons this morning, already over on the test clock (15:00 in Minsk). */
    private suspend fun morning(): List<Long> {
        val subject = env.fixtures.subject()
        return listOf("Аня", "Борис", "Вера").mapIndexed { i, name ->
            val student = env.fixtures.pricedStudent(name, subject)
            env.fixtures.lesson(subject, listOf(student), Instant.parse("2026-06-01T0${5 + i}:00:00Z")).single()
        }
    }

    private suspend fun status(id: Long) = env.classRepo.getSessionFor(id)!!.status

    @Test
    fun `marking a selection taught touches only the planned lessons, and one undo takes them back`() =
        runTest(mainRule.dispatcher) {
            val (a, b, c) = morning()
            env.classRepo.setStatus(env.classRepo.getSessionFor(c)!!, LessonStatus.CANCELLED)
            val batch = batch
            listOf(a, b, c).forEach(batch::toggle)

            batch.markTaught()
            runCurrent()
            val marked = batch.done.stream.first() as LessonBatch.Done.Marked

            assertEquals(2, marked.sessions.size)
            assertEquals(
                listOf(LessonStatus.COMPLETED, LessonStatus.COMPLETED, LessonStatus.CANCELLED),
                listOf(status(a), status(b), status(c)),
            )
            assertTrue("the selection closes with the action", batch.selected.value.isEmpty())

            batch.undo(marked)
            runCurrent()
            assertEquals(listOf(LessonStatus.SCHEDULED, LessonStatus.SCHEDULED), listOf(status(a), status(b)))
        }

    @Test
    fun `an undone batch cancel leaves a lesson changed since, and says how many`() = runTest(mainRule.dispatcher) {
        val (a, b) = morning()
        val batch = batch
        batch.toggle(a)
        batch.toggle(b)
        batch.toggle(b)
        batch.toggle(b)

        batch.cancel()
        runCurrent()
        val cancelled = batch.done.stream.first() as LessonBatch.Done.Cancelled
        assertEquals(LessonStatus.CANCELLED, status(a))

        env.classRepo.setStatus(env.classRepo.getSessionFor(b)!!, LessonStatus.SCHEDULED)
        batch.undo(cancelled)
        runCurrent()

        assertEquals(LessonStatus.SCHEDULED, status(a))
        assertEquals(LessonBatch.Done.ChangedSince(1), batch.done.stream.first())
    }
}
