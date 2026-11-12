package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookingConflictsTest : UiTest() {

    private val env = UiTestEnv()
    private val conflicts = BookingConflicts(
        env.classRepo,
        env.studentRepo.observeAll(),
        env.groupRepo.observeAll(),
        env.clock,
    )
    private val day: LocalDate = env.clock.today().plus(3, DateTimeUnit.DAY)

    @AfterTest
    fun closeDatabase() = env.close()

    private suspend fun lessonFor(name: String, at: LocalTime, on: LocalDate = day): Long {
        val student = env.fixtures.student(name = name)
        val subject = env.subjectRepo.createOrReuse("Алгебра")
        return env.fixtures.lesson(subject, listOf(student), on.atTime(at).toInstant(env.clock.zone)).single()
    }

    private fun form(at: LocalTime?, repeat: Repeat = Repeat.NONE, count: Int = 1) =
        ClassForm(date = day, timeFrom = at, durationMinutes = 60, repeat = repeat, repeatCount = count)

    private suspend fun clashes(form: ClassForm, editing: ClassSession? = null) =
        conflicts.clashes(flowOf(form), MutableStateFlow(editing)).first()

    @Test
    fun `a form without a time clashes with nothing`() = runTest {
        lessonFor("Борис", LocalTime(10, 0))

        assertEquals(BookingConflicts.Conflicts(), clashes(form(at = null)))
    }

    @Test
    fun `an overlapping lesson is named by its student`() = runTest {
        lessonFor("Борис", LocalTime(10, 0))

        val found = clashes(form(at = LocalTime(10, 30)))

        assertEquals("Борис", found.first?.who)
        assertEquals(0, found.available)
    }

    @Test
    fun `a repeating booking counts the weeks still free`() = runTest {
        lessonFor("Борис", LocalTime(10, 0), on = day.plus(1, DateTimeUnit.WEEK))

        val found = clashes(form(at = LocalTime(10, 0), repeat = Repeat.WEEKLY, count = 3))

        assertEquals("Борис", found.first?.who)
        assertEquals(2, found.available)
    }

    @Test
    fun `the lesson being edited does not stand in its own way`() = runTest {
        val id = lessonFor("Борис", LocalTime(10, 0))
        val editing = env.classRepo.getSessionFor(id)

        assertNull(clashes(form(at = LocalTime(10, 30)), editing).first)
    }

    @Test
    fun `the day leaves out the edited lesson and names the rest`() = runTest {
        val edited = lessonFor("Борис", LocalTime(10, 0))
        lessonFor("Аня", LocalTime(14, 0))

        val timeline = conflicts.day(
            flowOf(form(at = LocalTime(10, 0))),
            MutableStateFlow(env.classRepo.getSessionFor(edited)),
        ).first()

        assertEquals(listOf("Аня"), timeline?.booked?.map { it.who })
    }

    @Test
    fun `there is no day until the form has a date`() = runTest {
        assertNull(conflicts.day(flowOf(ClassForm()), MutableStateFlow(null)).first())
    }
}
