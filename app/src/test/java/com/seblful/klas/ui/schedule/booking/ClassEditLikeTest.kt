package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** «Book the next one like this»: a new booking of the same lesson, a week on. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditLikeTest : ClassEditTestFixture() {

    @Test
    fun `a lesson booked like another is the same lesson a week later, and nothing is written yet`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject(price = Money.ofMajor(25))
            val day = env.clock.today()
            val like = env.classRepo.book(
                subjectId = subjectId,
                start = day.atTime(16, 30).toInstant(zone),
                durationMinutes = 90,
                mode = ClassMode.REMOTE,
                studentIds = listOf(studentId),
                price = Money.ofMajor(25),
            ).written().single()

            val vm = vm(like = like)
            observing(vm.state)
            advanceUntilIdle()

            val form = vm.state.current.form
            assertTrue("it is a new booking", vm.isNew)
            assertEquals(studentId, form.studentId)
            assertEquals(subjectId, form.subjectId)
            assertEquals(day.plus(7, DateTimeUnit.DAY), form.date)
            assertEquals(LocalTime(16, 30), form.timeFrom)
            assertEquals(90, form.durationMinutes)
            assertEquals(ClassMode.REMOTE, form.mode)
            assertEquals(Money.ofMajor(25), form.price.amount)
            assertEquals("only the lesson it copies", 1, env.classRepo.getAll().size)
        }
}
