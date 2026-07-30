package com.seblful.klas.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun vm(id: Long = 0L, groupId: Long = 0L) = ClassEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id, "group" to groupId)),
        classes = env.classRepo,
        studentsRepo = env.studentRepo,
        groupsRepo = env.groupRepo,
        subjectsRepo = env.subjectRepo,
        enrollment = env.enrollmentRepo,
        settingsRepo = env.settingsRepo,
        appScope = mainRule.appScope,
        computation = mainRule.dispatcher,
        clock = Clock.systemDefaultZone(),
    )

    private suspend fun seedStudentWithPricedSubject(
        studentName: String = "Аня",
        subjectName: String = "Математика",
        price: Money = Money.ofMajor(30),
        mode: ClassMode = ClassMode.IN_PERSON,
    ): Pair<Long, Long> {
        val studentId = env.studentDao.upsert(StudentEntity(name = studentName, defaultMode = mode.name))
        val subjectId = env.subjectDao.upsert(SubjectEntity(name = subjectName))
        env.priceDao.upsert(PriceEntity(studentId, subjectId, price.minor))
        return studentId to subjectId
    }

    // --- Whether the form has arrived ---

    /**
     * **A lesson being edited waits for itself.** The state used to start at `ClassFormState(isNew)`,
     * which the screen draws the instant it appears while [ClassEditViewModel.loadExisting] is still a
     * coroutine — so «Перенести» on Борис's Tuesday opened with no student, no subject, today's date and
     * the default length, then replaced all four. See `ui/common/Loadable.kt`.
     */
    @Test
    fun `an existing lesson's form is Loading until the lesson has been read`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(2)
        val (_, id) = seedLesson(date, LocalTime.of(14, 0))

        val vm = vm(id)
        assertEquals(Loadable.Loading, vm.state.value)

        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(date, state.form.date)
        assertEquals(LocalTime.of(14, 0), state.form.timeFrom)
    }

    /**
     * And it waits for the **pickers** too, not only for the lesson — because the pickers are where the
     * form reads the names it shows. `state.students.firstOrNull { it.id == form.studentId }?.name` over a
     * list that has merely not been read yet is an empty «Ученик» field on a lesson that has one, which is
     * the same lie one frame later. So the first thing the screen is ever handed already names its lesson.
     */
    @Test
    fun `the form's first Ready state already names the lesson's student`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(2)
        val (studentId, id) = seedLesson(date, LocalTime.of(14, 0), studentName = "Борис")

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(studentId, state.form.studentId)
        assertEquals(
            "the picker the field reads the name out of has arrived with it",
            "Борис",
            state.students.firstOrNull { it.id == state.form.studentId }?.name,
        )
    }

    /** A new booking is [Loadable.Ready] at once: a blank form is what booking a lesson starts as. */
    @Test
    fun `a new booking is Ready without waiting`() = runTest(mainRule.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.canSave)
        assertNull(vm.state.current.form.date)
    }

    // --- ClassForm validation (pure) ---

    @Test
    fun `form is invalid until every required field is set`() {
        val empty = ClassForm()
        assertFalse(empty.isValid)
        assertNull(empty.durationMinutes)

        val full = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(10, 0),
            durationMinutes = 60,
            price = MoneyField("30"),
        )
        assertTrue(full.isValid)
        assertEquals(LocalTime.of(11, 0), full.ends?.time)
    }

    /**
     * A length of zero is a lesson that does not happen, and the form treats it as not filled in rather
     * than passing it to [Scheduling.Slot], which would throw. Nothing in the app produces one — the
     * chips are positive and «Другая» will not confirm at zero — so this is the guard, not a path.
     */
    @Test
    fun `a lesson of no length is not a lesson`() {
        val empty = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(11, 0),
            durationMinutes = 0,
            price = MoneyField("30"),
        )
        assertFalse(empty.isValid)
        assertNull(empty.ends)
        assertNull(empty.slot(ZoneId.of("Europe/Minsk")))
    }

    /**
     * The end is arithmetic on the start *instant*, so a late lesson simply finishes tomorrow. It used to
     * be a second wall clock on the same date, which made 23:00 + 1 ч read as a negative range: see
     * `EdgeCaseCombinationsTest`, where the limitation this replaced was pinned.
     */
    @Test
    fun `a lesson that runs past midnight ends the next day`() {
        val late = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(23, 0),
            durationMinutes = 90,
            price = MoneyField("30"),
        )
        assertTrue(late.isValid)
        assertEquals(LocalTime.of(0, 30), late.ends?.time)
        assertEquals(true, late.ends?.nextDay)
    }

    /** The flag is about passing midnight, not about the clock reading less than the start. */
    @Test
    fun `a lesson ending exactly at midnight is the next day, one ending before it is not`() {
        val toMidnight = ClassForm(timeFrom = LocalTime.of(23, 0), durationMinutes = 60)
        assertEquals(LocalTime.MIDNIGHT, toMidnight.ends?.time)
        assertEquals(true, toMidnight.ends?.nextDay)

        val justBefore = ClassForm(timeFrom = LocalTime.of(23, 0), durationMinutes = 59)
        assertEquals(LocalTime.of(23, 59), justBefore.ends?.time)
        assertEquals(false, justBefore.ends?.nextDay)
    }

    // --- Decisions the screen used to make for itself ---

    private fun stateWith(
        form: ClassForm = ClassForm(),
        subjects: List<Subject> = emptyList(),
        members: List<Student> = emptyList(),
        groups: List<Group> = emptyList(),
        subjectFixedByGroup: Boolean = false,
        conflict: TimeConflict? = null,
    ) = ClassFormState(
        form = form,
        subjects = subjects,
        members = members,
        groups = groups,
        subjectFixedByGroup = subjectFixedByGroup,
        conflict = conflict,
    )

    private val validForm = ClassForm(
        studentId = 1, subjectId = 1,
        date = LocalDate.of(2026, 6, 20),
        timeFrom = LocalTime.of(10, 0),
        durationMinutes = 60,
        price = MoneyField("30"),
    )

    private val someoneElse = TimeConflict(
        slot = Scheduling.Slot(Instant.parse("2026-06-20T07:00:00Z"), 60),
        who = "Борис",
    )

    /**
     * A clash is now the only thing that can be wrong about a chosen time, so it is also the only thing
     * that can stop a save. There used to be a `TimeProblem` deciding which of two complaints to show.
     */
    @Test
    fun `a taken slot stops the save even though the form itself is complete`() {
        val state = stateWith(form = validForm, conflict = someoneElse)

        assertTrue(state.form.isValid)
        assertFalse(state.canSave)
        assertTrue(stateWith(form = validForm).canSave)
    }

    @Test
    fun `a half-filled time is incomplete rather than wrong`() {
        // Only a start picked: nothing to complain about, and nothing to save either.
        val state = stateWith(form = ClassForm(timeFrom = LocalTime.of(10, 0)))

        assertNull(state.conflict)
        assertFalse(state.canSave)
    }

    @Test
    fun `the subject picker is dead until somebody is chosen`() {
        assertFalse(stateWith(form = ClassForm()).subjectPickerEnabled)
        assertTrue(stateWith(form = ClassForm(studentId = 1)).subjectPickerEnabled)
    }

    @Test
    fun `a group that fixes the subject leaves nothing to pick`() {
        val state = stateWith(
            form = ClassForm(target = ClassTarget.GROUP, groupId = 1),
            subjectFixedByGroup = true,
        )
        assertFalse(state.subjectPickerEnabled)
        assertEquals(SubjectNotice.FIXED_BY_GROUP, state.subjectNotice)
    }

    @Test
    fun `an empty subject list is explained differently for a student and a group`() {
        assertEquals(
            SubjectNotice.NONE_FOR_STUDENT,
            stateWith(form = ClassForm(studentId = 1)).subjectNotice,
        )
        assertEquals(
            SubjectNotice.NONE_FOR_GROUP,
            stateWith(form = ClassForm(target = ClassTarget.GROUP, groupId = 1)).subjectNotice,
        )
    }

    @Test
    fun `no notice is shown before a target is chosen, or once subjects exist`() {
        assertNull(stateWith(form = ClassForm()).subjectNotice)
        assertNull(
            stateWith(
                form = ClassForm(studentId = 1),
                subjects = listOf(Subject(id = 1, name = "Математика")),
            ).subjectNotice,
        )
    }

    @Test
    fun `unticking every member is recognised, and blocks the save`() {
        val ann = Student(id = 1, name = "Аня")
        val bob = Student(id = 2, name = "Борис")
        val form = validForm.copy(
            target = ClassTarget.GROUP, studentId = null, groupId = 7,
            excludedMemberIds = setOf(ann.id, bob.id),
        )
        val state = stateWith(form = form, members = listOf(ann, bob))

        assertTrue(state.allMembersExcluded)
        assertFalse("a lesson billing nobody cannot be saved", state.canSave)

        val oneLeft = stateWith(form = form.copy(excludedMemberIds = setOf(ann.id)), members = listOf(ann, bob))
        assertFalse(oneLeft.allMembersExcluded)
        assertTrue(oneLeft.canSave)
    }

    @Test
    fun `an individual lesson is never all-excluded`() {
        assertFalse(stateWith(form = validForm).allMembersExcluded)
    }

    @Test
    fun `a taken slot disables the save just as a missing field does`() {
        assertTrue(stateWith(form = validForm).canSave)
        assertFalse(stateWith(form = validForm, conflict = someoneElse).canSave)
        assertFalse(stateWith(form = validForm.copy(subjectId = null)).canSave)
    }

    @Test
    fun `booking for a group says so when there are no groups`() {
        assertTrue(stateWith(form = ClassForm(target = ClassTarget.GROUP)).hasNoGroups)
        assertFalse(
            stateWith(
                form = ClassForm(target = ClassTarget.GROUP),
                groups = listOf(Group(id = 1, name = "11 «А»", price = Money.ofMajor(20))),
            ).hasNoGroups,
        )
        assertFalse("a student booking has no opinion about groups", stateWith().hasNoGroups)
    }

    // --- Auto-selection on a new class ---

    @Test
    fun `a new class auto-selects the only student and their only priced subject and prefills the price`() = runTest(mainRule.dispatcher) {
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
    fun `no query stays open once nothing is collecting the form`() = runTest(mainRule.dispatcher) {
        seedStudentWithPricedSubject()
        val vm = vm()

        val watching = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue("the query is live while the screen watches", env.studentDao.items.subscriptionCount.value > 0)

        watching.cancel()
        // Past the sharing timeout, so WhileSubscribed has had its chance to stop the upstream.
        advanceTimeBy(10_000)
        advanceUntilIdle()

        assertEquals(
            "the students query is dropped once nothing is watching",
            0,
            env.studentDao.items.subscriptionCount.value,
        )
        assertEquals(
            "and so is the subject-price query",
            0,
            env.priceDao.items.subscriptionCount.value,
        )
    }

    // --- Unsaved changes: what the back arrow and the back gesture consult ---

    /**
     * The form fills itself in as it goes — the only student, their only subject, that subject's
     * price, the end time following the start. None of that is the tutor's work, so none of it may
     * make leaving ask whether to save.
     */
    @Test
    fun `everything the form fills in for itself leaves it clean`() = runTest(mainRule.dispatcher) {
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
    fun `a form opened already pointed at a group is clean`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(20), subjectId = subjectId),
            setOf(studentId),
        ).id

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
        assertEquals(groupId, vm.state.current.form.groupId)
        assertEquals("the group's rate was prefilled", "20", vm.state.current.form.price.text)
        assertFalse("opening the form from a group is not editing it", vm.state.current.isDirty)
    }

    @Test
    fun `loading a lesson to edit is clean, touching it is not, and saving it is clean again`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val start = LocalDate.of(2026, 6, 22).atTime(10, 0).atZone(zone).toInstant()
            val classId = env.fixtures.storedClass(
                    studentId = studentId, subjectId = subjectId,
                    startMillis = start.toEpochMilli(), durationMinutes = 60,
                    priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED.name,
                )

            val vm = vm(id = classId)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(LocalTime.of(10, 0), vm.state.current.form.timeFrom)
            assertFalse("loading the lesson is not editing it", vm.state.current.isDirty)

            vm.change(BookingIntent.StartAt(LocalTime.of(11, 0)))
            advanceUntilIdle()
            assertTrue(vm.state.current.isDirty)

            vm.save { }
            advanceUntilIdle()
            assertFalse("there is nothing unsaved left", vm.state.current.isDirty)
        }

    @Test
    fun `with several students none is auto-selected`() = runTest(mainRule.dispatcher) {
        env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Борис"))
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.current.form.studentId)
    }

    @Test
    fun `selecting a different student clears the subject and price and adopts the student's mode`() = runTest(mainRule.dispatcher) {
        // Two students, so nothing is auto-selected and selectStudent runs its clearing logic.
        env.studentDao.upsert(StudentEntity(name = "Аня", defaultMode = ClassMode.IN_PERSON.name))
        val remoteId = env.studentDao.upsert(StudentEntity(name = "Борис", defaultMode = ClassMode.REMOTE.name))
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
    fun `selecting a subject prefills its price only when the field is empty`() = runTest(mainRule.dispatcher) {
        val studentId = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        env.priceDao.upsert(PriceEntity(studentId, math, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(studentId, physics, Money.ofMajor(40).minor))
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // Two students? No — one student, two subjects, so nothing auto-selected.
        vm.change(BookingIntent.ChooseStudent(studentId))
        advanceUntilIdle()
        vm.change(BookingIntent.ChooseSubject(math))
        advanceUntilIdle()
        assertEquals("30", vm.state.current.form.price.text)

        // Switching subject keeps a price the user already has — selectSubject only fills an empty field.
        vm.change(BookingIntent.ChooseSubject(physics))
        advanceUntilIdle()
        assertEquals("30", vm.state.current.form.price.text)
    }

    @Test
    fun `picking a start time gives the lesson the default length`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime.of(10, 0)))
        advanceUntilIdle()
        assertEquals(90, vm.state.current.form.durationMinutes)
        assertEquals(LocalTime.of(11, 30), vm.state.current.form.ends?.time)
    }

    @Test
    fun `changing the start time keeps the lesson length`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // A 90-minute lesson, 10:00–11:30.
        vm.change(BookingIntent.StartAt(LocalTime.of(10, 0)))
        vm.change(BookingIntent.LastsFor(90))

        // Moving the start later leaves it 90 minutes long, now 14:00–15:30.
        vm.change(BookingIntent.StartAt(LocalTime.of(14, 0)))
        assertEquals(90, vm.state.current.form.durationMinutes)
        assertEquals(LocalTime.of(15, 30), vm.state.current.form.ends?.time)
    }

    @Test
    fun `the pickers open on the next whole hour and the default length while nothing is chosen`() =
        runTest(mainRule.dispatcher) {
            env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
            val vm = vm()
            advanceUntilIdle()

            // A lesson arranged at 14:37 is held at 15:00 — tutoring runs on the hour, so an empty form
            // rounds up rather than offering minutes the tutor would only spin away. The length is the
            // tutor's own.
            assertEquals(LocalTime.of(15, 0), vm.startPickerDefault(LocalTime.of(14, 37, 42)))
            assertEquals(90, vm.durationPickerDefault())
        }

    @Test
    fun `a time already on the hour is the hour it opens on`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        // Nothing to round: 15:00 is neither in the past nor short of the hour, so it must not be
        // pushed to 16:00.
        assertEquals(LocalTime.of(15, 0), vm.startPickerDefault(LocalTime.of(15, 0)))
        // Seconds are not minutes, and are below what the picker can even show — 15:00:20 is still
        // the 15:00 hour, not a reason to offer 16:00.
        assertEquals(LocalTime.of(15, 0), vm.startPickerDefault(LocalTime.of(15, 0, 20)))
    }

    @Test
    fun `the last hour of the day rounds up to midnight rather than overflowing`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            advanceUntilIdle()

            // The date is a separate field, so this is "the next hour", not a claim about which day.
            assertEquals(LocalTime.MIDNIGHT, vm.startPickerDefault(LocalTime.of(23, 40)))
        }

    @Test
    fun `the pickers reopen on what was already chosen`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime.of(10, 0)))
        vm.change(BookingIntent.LastsFor(50))

        assertEquals(LocalTime.of(10, 0), vm.startPickerDefault(LocalTime.of(14, 37)))
        assertEquals(50, vm.durationPickerDefault())
    }

    /** The last picker default the platform used to answer from inside a composable. */
    @Test
    fun `the date picker opens on the chosen date, or today`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        val today = LocalDate.of(2026, 7, 1)
        assertEquals(today, vm.datePickerDefault(today = today))

        vm.change(BookingIntent.Edit { it.copy(date = LocalDate.of(2026, 8, 9)) })
        assertEquals(LocalDate.of(2026, 8, 9), vm.datePickerDefault(today = today))
    }

    @Test
    fun `editing opens the pickers on the lesson's own time, not the current one`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED",
            )
        val vm = vm(id)
        advanceUntilIdle()

        val from = start.atZone(zone).toLocalTime()
        assertEquals(from, vm.startPickerDefault(LocalTime.of(14, 37)))
        assertEquals(60, vm.durationPickerDefault())
    }

    /**
     * The disagreement the form's second wall clock caused, pinned from the other end: «Перенести» moves a
     * lesson to any time it likes, so a 23:00 lesson can be sitting in the database, and opening one used
     * to reconstruct `timeTo = 00:00` and refuse to save the lesson it had just loaded — its price could
     * not be corrected without shortening it first.
     */
    @Test
    fun `a lesson that starts at 23 00 opens ready to save`() = runTest(mainRule.dispatcher) {
        // Booked through the repository, which has never had anything against a late lesson — the
        // refusal was the form's alone. «Перенести» reaches the same state by moving one there.
        val (_, id) = seedLesson(LocalDate.now(zone).plusDays(3), LocalTime.of(23, 0))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(60, state.form.durationMinutes)
        assertEquals(LocalTime.MIDNIGHT, state.form.ends?.time)
        assertEquals(true, state.form.ends?.nextDay)
        assertTrue("a lesson already in the schedule is one the form can save", state.canSave)
    }

    // --- Save: create ---

    @Test
    fun `saving a new class persists it with the chosen fields and signals done`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(3)
        vm.change(BookingIntent.Edit {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = LocalTime.of(10, 0), durationMinutes = 90,
                price = MoneyField("30"), mode = ClassMode.REMOTE,
            )
        })
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classDao.items.value.single()
        assertEquals(studentId, saved.studentId)
        assertEquals(subjectId, saved.subjectId)
        assertEquals(90, saved.durationMinutes)
        assertEquals(Money.ofMajor(30).minor, saved.priceMinor)
        assertEquals("SCHEDULED", saved.status)
        assertEquals("REMOTE", saved.mode)
    }

    /**
     * The write runs in the injected application scope, not in `viewModelScope` — so that going back
     * the instant after tapping save cannot cut it in half, between the rows it commits and the
     * calendar mirror and reminder refresh that follow them.
     *
     * Asserted from the other side, by cancelling the application scope: nothing is written. Were the
     * save launched into `viewModelScope` instead, that scope would still be live here and the lesson
     * would be booked regardless — which is what makes this a tripwire rather than a restatement.
     */
    @Test
    fun `the save is launched into the application scope, not the view model's`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.Edit {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = LocalDate.now(zone).plusDays(3),
                timeFrom = LocalTime.of(10, 0), durationMinutes = 60, price = MoneyField("30"),
            )
        })
        mainRule.appScope.cancel()

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse("no save can complete once the scope that owns writes is gone", done)
        assertTrue("and nothing was written", env.classDao.items.value.isEmpty())
    }

    // --- Save: a time that is already taken ---

    /** A booked lesson for [studentName] at [at] on [date], and the ids of its classes. */
    private suspend fun seedLesson(
        date: LocalDate,
        at: LocalTime,
        minutes: Int = 60,
        studentName: String = "Борис",
    ): Pair<Long, Long> {
        val (studentId, subjectId) = seedStudentWithPricedSubject(studentName = studentName)
        val id = env.classRepo.book(
            subjectId = subjectId,
            start = date.atTime(at).atZone(zone).toInstant(),
            durationMinutes = minutes,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(studentId),
            price = Money.ofMajor(30),
        ).written().single()
        return studentId to id
    }

    /** Fills the form for a lesson for [studentId] on [date], starting at [from] and lasting [minutes]. */
    private fun ClassEditViewModel.fillFor(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        from: LocalTime,
        minutes: Int,
    ) = change(
        BookingIntent.Edit {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = from, durationMinutes = minutes, price = MoneyField("30"),
            )
        },
    )

    @Test
    fun `a lesson cannot be booked over one that is already there`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        seedLesson(date, LocalTime.of(10, 0)) // Борис, 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // Аня at 10:30 would overlap Борис's lesson — the tutor cannot teach both.
        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 30), minutes = 60)
        advanceUntilIdle()

        val clash = vm.state.current.conflict
        assertEquals("Борис", clash?.who)
        assertEquals(date.atTime(10, 0).atZone(zone).toInstant(), clash?.slot?.start)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse("the save must not go through", done)
        assertEquals("still only the lesson that was there", 1, env.classDao.items.value.size)
    }

    @Test
    fun `a lesson starting exactly when another ends is allowed`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        seedLesson(date, LocalTime.of(10, 0)) // 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(11, 0), minutes = 60)
        advanceUntilIdle()
        assertNull(vm.state.current.conflict)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `a cancelled lesson frees its hour`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        val (_, bookedId) = seedLesson(date, LocalTime.of(10, 0))
        env.classRepo.setStatus(env.classRepo.getSessionFor(bookedId)!!, LessonStatus.CANCELLED)
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 0), minutes = 60)
        advanceUntilIdle()

        assertNull("a cancelled lesson is not happening, so the slot is free", vm.state.current.conflict)
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `re-saving the lesson being edited does not clash with itself`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        val (_, bookedId) = seedLesson(date, LocalTime.of(10, 0))

        val vm = vm(bookedId)
        observing(vm.state)
        advanceUntilIdle()

        assertNull("its own hour is not a conflict", vm.state.current.conflict)

        // Lengthen it in place — the overlap with its own old span must not block the save.
        vm.change(BookingIntent.Edit { it.copy(durationMinutes = 90) })
        advanceUntilIdle()
        assertNull(vm.state.current.conflict)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(90, env.classDao.items.value.single().durationMinutes)
    }

    @Test
    fun `a group lesson does not clash with its own members when re-saved`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        // A fixed 10:00 rather than the current time, so nothing here turns on when the suite runs.
        // It used to be load-bearing: loading a lesson rebuilt an end time from its length, so a run
        // after 23:00 opened this one as a negative range and the save quietly did nothing.
        val start = LocalDate.now(zone).plusDays(3).atTime(10, 0).atZone(zone).toInstant()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        // Every member's class is excluded, not just the one that was opened.
        assertNull(vm.state.current.conflict)
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `a group lesson in the way is reported by its group name`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val date = LocalDate.now(zone).plusDays(4)
        env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id,
            start = date.atTime(10, 0).atZone(zone).toInstant(),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()
        val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.fillFor(carl, physics, date, LocalTime.of(10, 30), minutes = 60)
        advanceUntilIdle()

        assertEquals("11 «А»", vm.state.current.conflict?.who)
        vm.save { }
        advanceUntilIdle()
        assertEquals("no third class was written", 2, env.classDao.items.value.size)
    }

    @Test
    fun `a save refused for a taken slot says so instead of doing nothing`() =
        runTest(mainRule.dispatcher) {
            val (occupier, subject) = seedStudentWithPricedSubject(studentName = "Борис")
            val date = LocalDate.now(zone).plusDays(3)
            env.classRepo.book(
                subjectId = subject, start = date.atTime(10, 0).atZone(zone).toInstant(),
                durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(occupier), price = Money.ofMajor(30),
            )
            val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

            val vm = vm()
            advanceUntilIdle()
            // Fill the form *without* collecting `conflict`, mimicking the race the button can't
            // catch: the slot was free when the form opened and is taken by the time save runs.
            vm.fillFor(carl, physics, date, LocalTime.of(10, 0), minutes = 60)
            advanceUntilIdle()

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse("nothing was written", done)
            assertEquals(1, env.classDao.items.value.size)
            assertEquals(
                ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN,
                vm.events.stream.first(),
            )
        }

    /**
     * A save that *fails* is not a save that was refused, and used to be neither: the write is handed
     * to the application scope as an `async`, so an exception crossed the `await` back into
     * `viewModelScope`, where nothing catches it — the app died on the one tap the tutor is most
     * certain of. The scope's own handler cannot help, because `async` holds a failure in its
     * `Deferred` rather than reporting it.
     */
    @Test
    fun `a save that fails to commit is reported and does not navigate`() =
        runTest(mainRule.dispatcher) {
            val (ann, subject) = seedStudentWithPricedSubject()
            val vm = vm()
            advanceUntilIdle()
            vm.fillFor(ann, subject, LocalDate.now(zone).plusDays(2), LocalTime.of(10, 0), minutes = 60)
            advanceUntilIdle()

            env.transactor.failAt = env.transactor.count + 1
            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse("a failed write must not navigate away as though it had saved", done)
            assertEquals(ClassEditViewModel.Event.SAVE_FAILED, vm.events.stream.first())
        }

    @Test
    fun `a group lesson with every member unticked is refused out loud`() =
        runTest(mainRule.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
            vm.change(BookingIntent.ChooseGroup(groupId))
            advanceUntilIdle()
            vm.change(BookingIntent.IncludeMember(ann, included = false))
            vm.change(BookingIntent.IncludeMember(bob, included = false))
            val date = LocalDate.now(zone).plusDays(3)
            vm.change(BookingIntent.Edit {
                it.copy(date = date, timeFrom = LocalTime.of(10, 0), durationMinutes = 60)
            })
            advanceUntilIdle()

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse(done)
            assertTrue("a lesson billing nobody is not written", env.classDao.items.value.isEmpty())
            assertEquals(
                ClassEditViewModel.Event.SAVE_REFUSED_NO_MEMBERS,
                vm.events.stream.first(),
            )
        }

    @Test
    fun `save does nothing for an invalid form`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        var done = false
        vm.save { done = true }
        advanceUntilIdle()
        assertFalse(done)
        assertTrue(env.classDao.items.value.isEmpty())
    }

    // --- Save: edit an existing class (move + rebill) ---

    @Test
    fun `editing loads the existing class into the form`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED",
            )
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.isNew)
        assertEquals(studentId, vm.state.current.form.studentId)
        assertEquals(subjectId, vm.state.current.form.subjectId)
        assertEquals("30", vm.state.current.form.price.text)
    }

    @Test
    fun `editing a completed class into the future re-plans it back to scheduled and voids the charge`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        // A fixed 10:00 so the status assertion cannot turn on the clock the suite runs at — and, back
        // when the form kept an end time, on that end having wrapped past midnight.
        val past = LocalDate.now(zone).minusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            )
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        // Move it to the future and save.
        vm.change(BookingIntent.Edit { it.copy(date = LocalDate.now(zone).plusDays(2)) })
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("SCHEDULED", saved.status)
        assertEquals(0L, saved.chargeMinor)
        // Still one row — an edit is an in-place update, never a duplicate insert.
        assertEquals(1, env.classDao.items.value.size)
    }

    // --- Group lessons ---

    /** The group's own hourly rate — deliberately unlike either member's individual price. */
    private val groupRate = Money.ofMajor(20)

    /**
     * Two students in one group. Both also carry an individual price for the subject, set to something
     * *other* than [groupRate], so any test that accidentally billed a member's own rate would fail.
     */
    private suspend fun seedGroup(
        annPrice: Money = Money.ofMajor(30),
        bobPrice: Money = Money.ofMajor(25),
        price: Money = groupRate,
    ): Triple<Long, Long, Long> {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.priceDao.upsert(PriceEntity(ann, algebra, annPrice.minor))
        env.priceDao.upsert(PriceEntity(bob, algebra, bobPrice.minor))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = price, defaultMode = ClassMode.REMOTE),
            setOf(ann, bob),
        ).id
        return Triple(groupId, ann, bob)
    }

    @Test
    fun `a form opened from a group starts out booking that group, in its default mode`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
        assertEquals(groupId, vm.state.current.form.groupId)
        assertEquals(ClassMode.REMOTE, vm.state.current.form.mode)
    }

    @Test
    fun `a group can be booked for any subject, priced for its members or not`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        // Nobody in the group has a Physics price. The lesson bills the group's rate, so that is no
        // longer a reason to withhold the subject.
        env.subjectDao.upsert(SubjectEntity(name = "Физика"))

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf("Алгебра", "Физика"), vm.state.current.subjects.map { it.name })
    }

    @Test
    fun `choosing a group prefills the lesson's rate from the group`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup(annPrice = Money.ofMajor(30), bobPrice = Money.ofMajor(25))
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        // The group's rate, not either member's individual price — and known from the group alone,
        // without waiting for a subject.
        assertEquals(groupRate, vm.state.current.form.price.amount)
        assertEquals(2, vm.state.current.members.size)
    }

    @Test
    fun `a rate already typed survives the group's prefill`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
        vm.change(BookingIntent.Edit { it.copy(price = MoneyField("35")) })
        vm.change(BookingIntent.ChooseGroup(groupId))
        advanceUntilIdle()

        assertEquals(
            "one lesson at an agreed rate isn't overruled by the group's",
            Money.ofMajor(35),
            vm.state.current.form.price.amount,
        )
    }

    @Test
    fun `an archived member is left out of a new group lesson`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.current.members.map { it.id })

        val date = LocalDate.now(zone).plusDays(2)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime.of(10, 0), durationMinutes = 60) })
        vm.save { }
        advanceUntilIdle()

        assertEquals("only the active member is billed", listOf(ann), env.classDao.items.value.map { it.studentId })
    }

    @Test
    fun `a group lesson needs one valid rate`() {
        val base = ClassForm(
            target = ClassTarget.GROUP,
            groupId = 7, subjectId = 1,
            date = LocalDate.of(2026, 9, 1),
            timeFrom = LocalTime.of(10, 0),
            durationMinutes = 60,
        )
        assertFalse("nothing said about the money", base.isValid)
        assertFalse(base.copy(price = MoneyField("abc")).isValid)
        assertTrue(base.copy(price = MoneyField("25,50")).isValid)
        assertTrue("a free lesson is billed at 0", base.copy(price = MoneyField("0")).isValid)
    }

    @Test
    fun `saving a new group lesson writes one class per member at the group's rate`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime.of(10, 0), durationMinutes = 90) })
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classDao.items.value
        assertEquals(2, saved.size)
        assertTrue("every class belongs to the group", saved.all { it.groupId == groupId })
        assertTrue("and to the same lesson", saved.map { it.startMillis }.distinct().size == 1)
        assertEquals(90, saved.first().durationMinutes)
        assertEquals(
            mapOf(ann to groupRate.minor, bob to groupRate.minor),
            saved.associate { it.studentId to it.priceMinor },
        )
    }

    @Test
    fun `a member excluded from this lesson gets no class at all`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime.of(10, 0), durationMinutes = 60) })
        // Bob is away this week. He stays on the roster; he just isn't at this lesson.
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classDao.items.value
        assertEquals(1, saved.size)
        assertEquals(ann, saved.single().studentId)
        // Excluding is not cancelling: there is no class of Bob's in any state.
        assertTrue("no cancelled leftover", saved.none { it.studentId == bob })
        assertEquals("the group keeps him", setOf(ann, bob), env.groupRepo.getMemberIds(groupId).toSet())
    }

    @Test
    fun `excluding everyone books nothing`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime.of(10, 0), durationMinutes = 60) })
        vm.change(BookingIntent.IncludeMember(ann, false))
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        vm.save { }
        advanceUntilIdle()

        // A lesson nobody attends is not a lesson; nothing is written rather than an ownerless row.
        assertTrue(env.classDao.items.value.isEmpty())
    }

    @Test
    fun `a group that has a subject decides the lesson's subject`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
        env.priceDao.upsert(PriceEntity(ann, algebra, Money.ofMajor(30).minor))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = groupRate, subjectId = algebra),
            setOf(ann),
        ).id

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        // Only the group's subject is on offer, it is already chosen, and the picker is locked.
        assertEquals(listOf(algebra), vm.state.current.subjects.map { it.id })
        assertEquals(algebra, vm.state.current.form.subjectId)
        assertTrue(vm.state.current.subjectFixedByGroup)
        assertTrue("physics is not bookable for this group", vm.state.current.subjects.none { it.id == physics })
    }

    @Test
    fun `a group with no subject still offers every subject`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        env.subjectDao.upsert(SubjectEntity(name = "Физика"))

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        // A group with no subject keeps choosing per lesson.
        assertEquals(2, vm.state.current.subjects.size)
        assertFalse(vm.state.current.subjectFixedByGroup)
    }

    @Test
    fun `editing a group lesson loads every member and saves them all`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = Instant.parse("2026-09-01T09:00:00Z")
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        // Opening it by *one* member's class must still edit the whole lesson.
        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
        assertEquals("the rate the lesson was booked at", groupRate, vm.state.current.form.price.amount)
        assertEquals(listOf(ann, bob), vm.state.current.members.map { it.id })

        // Re-price the lesson and lengthen it; both members' classes must be updated in place.
        vm.change(BookingIntent.Edit { it.copy(price = MoneyField("28"), durationMinutes = 90) })
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.durationMinutes == 90 })
        assertTrue("one rate applies to the whole lesson", saved.all { it.priceMinor == Money.ofMajor(28).minor })
    }

    /**
     * The list offered on an old lesson is the group as it is *now* plus whoever the lesson covers —
     * that is what makes a correction possible either way — but only the lesson's own members start
     * ticked, so opening it and saving it again is a no-op.
     */
    @Test
    fun `editing an old lesson offers the current roster but ticks only its own members`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val algebra = env.subjectDao.items.value.first().id
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = algebra, start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()
        // Bob leaves the group and a new student joins, *after* the lesson was booked.
        val carl = env.studentDao.upsert(StudentEntity(name = "Вова"))
        env.priceDao.upsert(PriceEntity(carl, algebra, Money.ofMajor(20).minor))
        env.groupRepo.save(Group(id = groupId, name = "11 «А»"), setOf(ann, carl))

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        // Bob is still listed though he left the group — he is in this lesson and can be taken out
        // of it. Carl is listed because he could be added to it, but unticked because he wasn't there.
        assertEquals(listOf(ann, bob, carl), vm.state.current.members.map { it.id })
        assertEquals(setOf(carl), vm.state.current.form.excludedMemberIds)
        assertFalse("loading a lesson is not the tutor's edit", vm.state.current.isDirty)

        // Re-saving must not create a class for the newcomer, nor drop the departed member's.
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classDao.items.value.size)
        assertEquals(setOf(ann, bob), env.classDao.items.value.map { it.studentId }.toSet())
    }

    @Test
    fun `unticking a member of a booked lesson removes their class on save`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.IncludeMember(bob, false))
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.classDao.items.value.map { it.studentId })
    }

    @Test
    fun `ticking a group member who was not in a booked lesson adds their class`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()
        assertEquals("Bob is offered, unticked", setOf(bob), vm.state.current.form.excludedMemberIds)

        vm.change(BookingIntent.IncludeMember(bob, true))
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value
        assertEquals(setOf(ann, bob), saved.map { it.studentId }.toSet())
        assertTrue("the joiner is in the same lesson", saved.all { it.groupId == groupId })
        assertEquals(1, saved.map { it.startMillis }.distinct().size)
    }

    @Test
    fun `emptying a booked lesson of every member is refused, not saved`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.IncludeMember(ann, false))
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        assertTrue(vm.state.current.allMembersExcluded)
        assertFalse("nothing left to bill", vm.state.current.canSave)
        assertEquals("both classes are still there", 2, env.classDao.items.value.size)
    }

    /** Removing somebody who has already been billed is worth a question, so the form counts them. */
    @Test
    fun `unticking a billed member is counted, an unbilled one is not`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.items.value.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()
        // Only Ann's class has billed; Bob was not there, so his was not charged.
        val session = env.classRepo.getSessionFor(ids.first())!!
        env.classRepo.setStatus(session, LessonStatus.COMPLETED)
        val bobsClass = env.classRepo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        env.classRepo.setAttendance(bobsClass, Attended.ABSENT)

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("nothing unticked yet", 0, vm.state.current.billedDrops)
        assertEquals("so saving asks nothing", SaveIntent.Save, vm.state.current.saveIntent)

        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()
        assertEquals("an absent member earned nothing to lose", 0, vm.state.current.billedDrops)
        assertEquals(SaveIntent.Save, vm.state.current.saveIntent)

        vm.change(BookingIntent.IncludeMember(ann, false))
        advanceUntilIdle()
        assertEquals(1, vm.state.current.billedDrops)
        // The gate and the count the question needs, together. Both were in the composable, where the
        // count was also kept as a second copy in screen state.
        assertEquals(SaveIntent.ConfirmBilledDrops(1), vm.state.current.saveIntent)
    }

    @Test
    fun `switching the target clears the other target's choices`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
        advanceUntilIdle()
        assertEquals(groupId, vm.state.current.form.groupId) // the only group is auto-selected
        assertNull(vm.state.current.form.studentId)

        vm.change(BookingIntent.ChooseTarget(ClassTarget.STUDENT))
        advanceUntilIdle()
        assertNull(vm.state.current.form.groupId)
        assertTrue("the group's rate goes with the group", vm.state.current.form.price.text.isEmpty())
    }

    @Test
    fun `editing a completed class in the past rebills it from the new price and duration`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        // A fixed 10:00 rather than a week ago exactly: this test lengthens the lesson to 90 minutes,
        // which — while the form held an end time — pushed it past midnight on an evening run and made
        // the charge assertion fail for the time of day rather than for the rule.
        val past = LocalDate.now(zone).minusDays(7).atTime(10, 0).atZone(zone).toInstant()
        val id = env.fixtures.storedClass(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            )
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        // Keep it in the past (its own date), change price to 40/h and duration to 90 min → charge 60.
        val pastDate = past.atZone(zone).toLocalDate()
        val from = past.atZone(zone).toLocalTime()
        vm.change(BookingIntent.Edit {
            it.copy(date = pastDate, timeFrom = from, durationMinutes = 90, price = MoneyField("40"))
        })
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("COMPLETED", saved.status)
        assertEquals(Money.ofMajor(60).minor, saved.chargeMinor)
    }
}
