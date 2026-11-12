package com.seblful.klas.ui.students

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.people.LessonLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StudentsViewModelTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    private val main = TestMain()

    private val env = UiTestEnv()

    private fun vm() = main.own(
        StudentsViewModel(
            env.studentRepo, env.balanceRepo, env.classRepo, env.enrollmentRepo, env.subjectRepo, env.clock,
            main.dispatcher,
        ),
    )

    // The environment's clock reads Monday 1 June 2026, 15:00 in Minsk (12:00 UTC).

    @Test
    fun `a card says what a student takes and their next lesson - or their last with none booked`() =
        runTest(main.dispatcher) {
            val maths = env.fixtures.subject("Математика")
            val physics = env.fixtures.subject("Физика")
            val anya = env.fixtures.pricedStudent("Аня", physics)
            env.enrollmentRepo.enroll(anya, maths, Money.ofMajor(30))
            val boris = env.fixtures.pricedStudent("Борис", maths)
            env.fixtures.student("Вера")
            env.fixtures.completedLesson(maths, listOf(anya), Instant.parse("2026-05-20T10:00:00Z"))
            // Called off, so the Wednesday after it is the next lesson.
            env.fixtures.cancelledLesson(maths, listOf(anya), Instant.parse("2026-06-02T10:00:00Z"))
            env.fixtures.lesson(maths, listOf(anya), Instant.parse("2026-06-03T13:00:00Z"))
            env.fixtures.completedLesson(maths, listOf(boris), Instant.parse("2026-05-21T10:00:00Z"))

            val vm = vm()
            observing(vm.rows)
            runCurrent()

            val rows = vm.rows.current.associateBy { it.student.name }
            assertEquals(listOf("Математика", "Физика"), rows.getValue("Аня").subjects)
            assertEquals(
                LessonLine.Next(LocalDate(2026, 6, 3), LocalTime(16, 0), daysAway = 2, thisYear = 2026),
                rows.getValue("Аня").lessons,
            )
            assertEquals(LessonLine.Last(LocalDate(2026, 5, 21), thisYear = 2026), rows.getValue("Борис").lessons)
            assertEquals(LessonLine.Never, rows.getValue("Вера").lessons)
        }

    @Test
    fun `a lesson that ends while the list is open becomes the last one`() = runTest(main.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val anya = env.fixtures.pricedStudent("Аня", maths)
        env.fixtures.lesson(maths, listOf(anya), Instant.parse("2026-06-01T11:30:00Z"))
        val vm = vm()
        observing(vm.rows)
        runCurrent()
        assertEquals(
            LessonLine.Next(LocalDate(2026, 6, 1), LocalTime(14, 30), daysAway = 0, thisYear = 2026),
            vm.rows.current.single().lessons,
        )

        val pastItsEnd = 31.minutes
        env.clock.advanceBy(pastItsEnd.inWholeMilliseconds)
        advanceTimeBy(pastItsEnd.inWholeMilliseconds)

        assertEquals(LessonLine.Last(LocalDate(2026, 6, 1), thisYear = 2026), vm.rows.current.single().lessons)
    }

    @Test
    fun `the list is Loading until read - and Ready even when there is nothing`() = runTest(main.dispatcher) {
        val vm = vm()

        assertEquals(Loadable.Loading, vm.rows.value)

        observing(vm.rows)
        runCurrent()

        assertEquals(Loadable.Ready(emptyList<StudentRow>()), vm.rows.value)
    }

    @Test
    fun `active list excludes archived students and carries each balance`() = runTest(main.dispatcher) {
        val active = env.fixtures.student(name = "Аня")
        env.fixtures.student(name = "Старый", archived = true)
        val subj = env.fixtures.subject(name = "Математика")
        env.fixtures.storedClass(
            studentId = active,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
            chargeMinor = Money.ofMajor(30).minor,
        )
        env.paymentRepo.restore(
            Payment(studentId = active, date = Instant.fromEpochMilliseconds(1), amount = Money.ofMajor(10)),
        )

        val vm = vm()
        observing(vm.rows)
        runCurrent()

        assertEquals(listOf("Аня"), vm.rows.current.map { it.student.name })
        assertEquals(Money.ofMajor(20), vm.rows.current.single().balance)
    }

    @Test
    fun `Owes narrows the list to those who owe - and counts them`() = runTest(main.dispatcher) {
        val owes = env.fixtures.student(name = "Аня")
        env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Математика")
        env.fixtures.storedClass(
            studentId = owes,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
            chargeMinor = Money.ofMajor(30).minor,
        )

        val vm = vm()
        observing(vm.rows, vm.owing)
        runCurrent()
        assertEquals(1, vm.owing.value)
        assertEquals(2, vm.rows.current.size)

        vm.toggleOwes()
        runCurrent()

        assertEquals(listOf("Аня"), vm.rows.current.map { it.student.name })
    }

    @Test
    fun `toggling archived shows all students including archived ones`() = runTest(main.dispatcher) {
        env.fixtures.student(name = "Аня")
        env.fixtures.student(name = "Старый", archived = true)

        val vm = vm()
        observing(vm.rows)
        runCurrent()
        assertEquals(1, vm.rows.current.size)

        vm.toggleArchived()
        runCurrent()
        assertEquals(2, vm.rows.current.size)
    }

    @Test
    fun `with the archive shown - the archived come after everyone taught now`() = runTest(main.dispatcher) {
        env.fixtures.archivedStudent("Аня")
        env.fixtures.student("Борис")
        val vm = vm()
        observing(vm.rows)
        vm.toggleArchived()
        runCurrent()

        assertEquals(listOf("Борис", "Аня"), vm.rows.current.map { it.student.name })
    }
}
