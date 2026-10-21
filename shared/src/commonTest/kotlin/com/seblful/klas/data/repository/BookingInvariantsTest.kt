package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class BookingInvariantsTest {
    @Test
    fun `generated bookings agree with interval arithmetic and leave no overlaps`() = runTest {
        TestEnv().use { env ->
            val subject = env.fixtures.subject()
            val student = env.fixtures.pricedStudent(subjectId = subject)
            val seed = 61704
            val random = Random(seed)
            val accepted = mutableListOf<Pair<Int, Int>>()
            val base = env.clock.now() + 1.days
            repeat(200) { case ->
                val from = random.nextInt(0, 600)
                val until = from + random.nextInt(5, 121)
                val context = "seed=$seed case=$case interval=$from..$until"
                val clashes = accepted.any { (a, b) -> from < b && a < until }
                val outcome = env.classRepo.book(
                    subject, base + from.minutes, until - from, ClassMode.IN_PERSON, listOf(student), Money(3000),
                )
                if (clashes) {
                    assertIs<LessonWrite.HourTaken>(outcome, context)
                } else {
                    assertEquals(1, outcome.written().size, context)
                    accepted += from to until
                }
                assertEquals(accepted.size, env.classes().size, context)
            }
            val rows = env.classRepo.getAll().sortedBy { it.start }
            rows.zipWithNext().forEach { (a, b) ->
                assertTrue(a.start + a.durationMinutes.minutes <= b.start, "seed=$seed")
            }
        }
    }
}
