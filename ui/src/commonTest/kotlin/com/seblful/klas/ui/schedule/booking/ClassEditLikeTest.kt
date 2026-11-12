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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Book the next one like this»: a new booking of the same lesson, a week on. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditLikeTest : ClassEditTestFixture() {

    @Test
    fun `a lesson booked like another is the same lesson a week later - and nothing is written yet`() =
        runTest(main.dispatcher) {
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
            assertTrue(vm.isNew, "it is a new booking")
            assertEquals(studentId, form.studentId)
            assertEquals(subjectId, form.subjectId)
            assertEquals(day.plus(7, DateTimeUnit.DAY), form.date)
            assertEquals(LocalTime(16, 30), form.timeFrom)
            assertEquals(90, form.durationMinutes)
            assertEquals(ClassMode.REMOTE, form.mode)
            assertEquals(Money.ofMajor(25), form.price.amount)
            assertEquals(1, env.classRepo.getAll().size, "only the lesson it copies")
        }
}
