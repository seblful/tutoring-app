package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditFormTest : ClassEditTestFixture() {

    @Test
    fun `profile booking uses the named student and normal rate and conflict checks`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        env.fixtures.pricedStudent("Other", subject)
        val student = env.fixtures.pricedStudent("Chosen", subject, rate = Money.ofMajor(45))
        val vm = vm(studentId = student)
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(student, vm.state.current.form.studentId)
        assertEquals(subject, vm.state.current.form.subjectId)
        assertEquals(Money.ofMajor(45), vm.state.current.form.price.amount)
        assertFalse(vm.state.current.isDirty)
    }

    @Test
    fun `returning from student creation preserves booking time and selects the new student`() =
        runTest(main.dispatcher) {
            val subject = env.fixtures.subject()
            val vm = vm()
            observing(vm.state)
            val day = LocalDate(2027, 1, 5)
            vm.change(BookingIntent.Edit { it.copy(date = day, timeFrom = LocalTime(14, 0)) })
            val student = env.fixtures.pricedStudent("Created", subject)
            vm.selectCreatedStudent(student)
            advanceUntilIdle()
            assertEquals(day, vm.state.current.form.date)
            assertEquals(LocalTime(14, 0), vm.state.current.form.timeFrom)
            assertEquals(student, vm.state.current.form.studentId)
            assertEquals(subject, vm.state.current.form.subjectId)
        }

    @Test
    fun `an existing lesson's form is Loading until the lesson has been read`() = runTest(main.dispatcher) {
        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        val (_, id) = seedLesson(date, LocalTime(14, 0))

        val vm = vm(id)
        assertEquals(Loadable.Loading, vm.state.value)

        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(date, state.form.date)
        assertEquals(LocalTime(14, 0), state.form.timeFrom)
    }

    /**
     * And it waits for the **pickers** too, not only for the lesson — because the pickers are where the
     * form reads the names it shows. `state.students.firstOrNull { it.id == form.studentId }?.name` over a
     * list that has merely not been read yet is an empty «Ученик» field on a lesson that has one, which is
     * the same lie one frame later. So the first thing the screen is ever handed already names its lesson.
     *
     * The name is now on the state rather than looked up out of `students` — see
     * [ClassFormState.studentName] — so this asks the field the screen actually draws.
     */
    @Test
    fun `the form's first Ready state already names the lesson's student`() = runTest(main.dispatcher) {
        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        val (studentId, id) = seedLesson(date, LocalTime(14, 0), studentName = "Борис")

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(studentId, state.form.studentId)
        assertEquals("Борис", state.studentName, "the field says who the lesson is for")
        assertEquals("Математика", state.subjectName)
    }

    /**
     * **An archived student's lesson still names them.**
     *
     * Archiving cancels what was still planned and leaves the rest, and editing is allowed in any status,
     * so a retired student's lessons remain openable. The picker read `observeActive()`, so the form had
     * no row to resolve the name from and drew «Ученик» blank over a lesson that has one — with
     * `canSave` still true, i.e. offering to re-save a lesson it was misrepresenting. Groups were already
     * spared this by re-adding the edited lesson's own group; students now are too.
     */
    @Test
    fun `an archived student's lesson still names them`() = runTest(main.dispatcher) {
        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        val (studentId, id) = seedLesson(date, LocalTime(14, 0), studentName = "Аня")
        env.studentRepo.setArchived(env.studentRepo.getById(studentId)!!, archived = true)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(studentId, state.form.studentId)
        assertEquals("Аня", state.studentName)
        assertTrue(
            state.students.any {
                it.id ==
                    studentId
            },
            "and is still offered, so the picker can show its own selection",
        )
    }

    /**
     * The allowance is exactly the lesson being edited: every *other* archived student stays out, or the
     * picker would offer people the tutor has retired.
     */
    @Test
    fun `another archived student is still left out of the picker`() = runTest(main.dispatcher) {
        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        val (studentId, id) = seedLesson(date, LocalTime(14, 0), studentName = "Аня")
        val other = env.fixtures.student("Глеб")
        env.studentRepo.setArchived(env.studentRepo.getById(studentId)!!, archived = true)
        env.studentRepo.setArchived(env.studentRepo.getById(other)!!, archived = true)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(studentId), vm.state.current.students.map { it.id })
    }

    /** A new booking is [Loadable.Ready] at once: a blank form is what booking a lesson starts as. */
    @Test
    fun `a new booking is Ready without waiting`() = runTest(main.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.canSave)
        assertNull(vm.state.current.form.date)
    }

    @Test
    fun `a new class auto-selects the only student and their only priced subject and prefills the price`() =
        runTest(main.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject(price = Money.ofMajor(30))
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(studentId, vm.state.current.form.studentId)
            assertEquals(subjectId, vm.state.current.form.subjectId)
            assertEquals("30", vm.state.current.form.price.text)
        }

    /**
     * The form's shared flows say `WhileSubscribed(5_000)`, and that has to be true: once the screen
     * stops watching, the queries behind it must actually stop.
     *
     * They used to not. Two `init` collectors — auto-selecting the only student and the only subject —
     * subscribed for the view model's whole life, so the timeout could never fire and the student,
     * price and subject queries stayed live behind a form nobody was looking at. Both are one-shot
     * reads now, and this is what would notice them turning back into collectors.
     */
    @Test
    fun `no query stays open once nothing is collecting the form`() = runTest(main.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm()

        val watching = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(
            "students" in env.database.watchedTables(),
            "the query is live while the screen watches",
        )

        watching.cancel()
        // Past the sharing timeout, so WhileSubscribed has had its chance to stop the upstream.
        advanceTimeBy(10_000)
        advanceUntilIdle()

        val watched = env.database.watchedTables()
        assertFalse("students" in watched, "the students query is dropped once nothing is watching")
        assertFalse("prices" in watched, "and so is the subject-price query")
    }

    /**
     * The form fills itself in as it goes — the only student, their only subject, that subject's
     * price, the end time following the start. None of that is the tutor's work, so none of it may
     * make leaving ask whether to save.
     */
    @Test
    fun `everything the form fills in for itself leaves it clean`() = runTest(main.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // A student and a subject have been chosen — by the app, not by anybody.
        assertNotNull(vm.state.current.form.studentId)
        assertNotNull(vm.state.current.form.subjectId)
        assertFalse(vm.state.current.isDirty)
    }

    @Test
    fun `a form opened already pointed at a group is clean`() = runTest(main.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(20), subjectId = subjectId),
            setOf(studentId),
        )

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
        assertEquals(groupId, vm.state.current.form.groupId)
        assertEquals("20", vm.state.current.form.price.text, "the group's rate was prefilled")
        assertFalse(vm.state.current.isDirty, "opening the form from a group is not editing it")
    }

    @Test
    fun `loading a lesson to edit is clean - touching it is not - and saving it is clean again`() =
        runTest(main.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val start = LocalDate(2026, 6, 22).atTime(10, 0).toInstant(zone)
            val classId = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId,
                startMillis = start.toEpochMilliseconds(), durationMinutes = 60,
                priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
            )

            val vm = vm(id = classId)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(LocalTime(10, 0), vm.state.current.form.timeFrom)
            assertFalse(vm.state.current.isDirty, "loading the lesson is not editing it")

            vm.change(BookingIntent.StartAt(LocalTime(11, 0)))
            advanceUntilIdle()
            assertTrue(vm.state.current.isDirty)

            vm.save { }
            advanceUntilIdle()
            assertFalse(vm.state.current.isDirty, "there is nothing unsaved left")
        }

    @Test
    fun `with several students none is auto-selected`() = runTest(main.dispatcher) {
        env.fixtures.student(name = "Аня")
        env.fixtures.student(name = "Борис")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.current.form.studentId)
    }

    @Test
    fun `selecting a different student clears the subject and price and adopts the student's mode`() =
        runTest(main.dispatcher) {
            // Two students, so nothing is auto-selected and selectStudent runs its clearing logic.
            env.fixtures.student(name = "Аня", defaultMode = ClassMode.IN_PERSON)
            val remoteId = env.fixtures.student(name = "Борис", defaultMode = ClassMode.REMOTE)
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            vm.change(BookingIntent.Edit { it.copy(subjectId = 99, price = MoneyField("50")) })
            vm.change(BookingIntent.ChooseStudent(remoteId))
            advanceUntilIdle()

            assertEquals(remoteId, vm.state.current.form.studentId)
            assertNull(vm.state.current.form.subjectId)
            assertEquals("", vm.state.current.form.price.text)
            assertEquals(ClassMode.REMOTE, vm.state.current.form.mode)
        }

    @Test
    fun `subject changes refresh automatic rates and preserve typed overrides`() = runTest(main.dispatcher) {
        val studentId = env.fixtures.student(name = "Аня")
        val math = env.fixtures.subject(name = "Математика")
        val physics = env.fixtures.subject(name = "Физика")
        env.enrollmentRepo.enroll(studentId, math, Money.ofMajor(30))
        env.enrollmentRepo.enroll(studentId, physics, Money.ofMajor(40))
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // Two students? No — one student, two subjects, so nothing auto-selected.
        vm.change(BookingIntent.ChooseStudent(studentId))
        advanceUntilIdle()
        vm.change(BookingIntent.ChooseSubject(math))
        advanceUntilIdle()
        assertEquals("30", vm.state.current.form.price.text)

        vm.change(BookingIntent.ChooseSubject(physics))
        advanceUntilIdle()
        assertEquals("40", vm.state.current.form.price.text)
        vm.change(BookingIntent.Edit { it.copy(price = it.price.typed("35")) })
        vm.change(BookingIntent.ChooseSubject(math))
        advanceUntilIdle()
        assertEquals("35", vm.state.current.form.price.text)
    }

    /**
     * Opened for a lesson deleted since — a stale reminder's «Перенести» — the form says so instead of
     * opening blank, and a save from it books nothing: it used to go down the booking path.
     */
    @Test
    fun `a lesson that is gone opens as gone and saves nothing`() = runTest(main.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm(id = 4242)
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.gone.value)
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertFalse(saved)
        assertTrue(env.classRepo.getAll().isEmpty(), "nothing was booked in its place")
    }

    @Test
    fun `editing loads the existing class into the form`() = runTest(main.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.fixtures.storedClass(
            studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilliseconds(),
            durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
        )
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.isNew)
        assertEquals(studentId, vm.state.current.form.studentId)
        assertEquals(subjectId, vm.state.current.form.subjectId)
        assertEquals("30", vm.state.current.form.price.text)
    }
}
