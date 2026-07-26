package com.seblful.repetit.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.ClassSession
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.format.MoneyField
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
            timeTo = LocalTime.of(11, 0),
            price = MoneyField("30"),
        )
        assertTrue(full.isValid)
        assertEquals(60, full.durationMinutes)
    }

    @Test
    fun `a non-positive time range yields a null duration and an invalid form`() {
        val backwards = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(11, 0),
            timeTo = LocalTime.of(10, 0),
            price = MoneyField("30"),
        )
        assertNull(backwards.durationMinutes)
        assertFalse(backwards.isValid)

        val zeroLength = backwards.copy(timeTo = LocalTime.of(11, 0))
        assertNull(zeroLength.durationMinutes)
        assertFalse(zeroLength.isValid)
    }

    @Test
    fun `a class cannot be booked across midnight (known single-day limitation)`() {
        // timeFrom/timeTo are wall-clock LocalTimes on one date, so 23:00 -> 00:30 is a negative
        // range and the form rejects it. This pins the current single-day behaviour.
        val crossingMidnight = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(23, 0),
            timeTo = LocalTime.of(0, 30),
            price = MoneyField("30"),
        )
        assertNull(crossingMidnight.durationMinutes)
        assertFalse(crossingMidnight.isValid)
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
        timeTo = LocalTime.of(11, 0),
        price = MoneyField("30"),
    )

    private val someoneElse = TimeConflict(
        slot = com.seblful.repetit.domain.Scheduling.Slot(Instant.parse("2026-06-20T07:00:00Z"), 60),
        who = "Борис",
    )

    @Test
    fun `an impossible time range is reported ahead of a clash`() {
        // Both are wrong at once. The tutor's own backwards range is the one they can fix here, so
        // it wins; reporting the clash would send them looking at somebody else's lesson.
        val state = stateWith(
            form = validForm.copy(timeFrom = LocalTime.of(11, 0), timeTo = LocalTime.of(10, 0)),
            conflict = someoneElse,
        )
        assertEquals(TimeProblem.RangeInvalid, state.timeProblem)
    }

    @Test
    fun `a clash is reported once the range itself is fine`() {
        val state = stateWith(form = validForm, conflict = someoneElse)
        assertEquals(TimeProblem.Taken(someoneElse), state.timeProblem)
    }

    @Test
    fun `a workable time reports no problem`() {
        assertNull(stateWith(form = validForm).timeProblem)
    }

    @Test
    fun `a half-filled time reports no problem yet`() {
        // Only a start picked: incomplete, not wrong.
        assertNull(stateWith(form = ClassForm(timeFrom = LocalTime.of(10, 0))).timeProblem)
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(studentId, vm.form.value.studentId)
        assertEquals(subjectId, vm.form.value.subjectId)
        assertEquals("30", vm.form.value.price.text)
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // A student and a subject have been chosen — by the app, not by anybody.
        assertNotNull(vm.form.value.studentId)
        assertNotNull(vm.form.value.subjectId)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `a form opened already pointed at a group is clean`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(studentId, subjectId))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(20), subjectId = subjectId),
            setOf(studentId),
        )

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.form.value.target)
        assertEquals(groupId, vm.form.value.groupId)
        assertEquals("the group's rate was prefilled", "20", vm.form.value.price.text)
        assertFalse("opening the form from a group is not editing it", vm.state.value.isDirty)
    }

    @Test
    fun `loading a lesson to edit is clean, touching it is not, and saving it is clean again`() =
        runTest(mainRule.dispatcher) {
            val (studentId, subjectId) = seedStudentWithPricedSubject()
            val start = LocalDate.of(2026, 6, 22).atTime(10, 0).atZone(zone).toInstant()
            val classId = env.classDao.upsert(
                ClassEntity(
                    studentId = studentId, subjectId = subjectId,
                    startMillis = start.toEpochMilli(), durationMinutes = 60,
                    priceMinor = Money.ofMajor(30).minor, status = ClassStatus.SCHEDULED.name,
                )
            )

            val vm = vm(id = classId)
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(LocalTime.of(10, 0), vm.form.value.timeFrom)
            assertFalse("loading the lesson is not editing it", vm.state.value.isDirty)

            vm.setTimeFrom(LocalTime.of(11, 0))
            advanceUntilIdle()
            assertTrue(vm.state.value.isDirty)

            vm.save { }
            advanceUntilIdle()
            assertFalse("there is nothing unsaved left", vm.state.value.isDirty)
        }

    @Test
    fun `with several students none is auto-selected`() = runTest(mainRule.dispatcher) {
        env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Борис"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull(vm.form.value.studentId)
    }

    @Test
    fun `selecting a different student clears the subject and price and adopts the student's mode`() = runTest(mainRule.dispatcher) {
        // Two students, so nothing is auto-selected and selectStudent runs its clearing logic.
        env.studentDao.upsert(StudentEntity(name = "Аня", defaultMode = ClassMode.IN_PERSON.name))
        val remoteId = env.studentDao.upsert(StudentEntity(name = "Борис", defaultMode = ClassMode.REMOTE.name))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.update { it.copy(subjectId = 99, price = MoneyField("50")) }
        vm.selectStudent(remoteId)
        advanceUntilIdle()

        assertEquals(remoteId, vm.form.value.studentId)
        assertNull(vm.form.value.subjectId)
        assertEquals("", vm.form.value.price.text)
        assertEquals(ClassMode.REMOTE, vm.form.value.mode)
    }

    @Test
    fun `selecting a subject prefills its price only when the field is empty`() = runTest(mainRule.dispatcher) {
        val studentId = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        env.priceDao.upsert(PriceEntity(studentId, math, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(studentId, physics, Money.ofMajor(40).minor))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Two students? No — one student, two subjects, so nothing auto-selected.
        vm.selectStudent(studentId)
        advanceUntilIdle()
        vm.selectSubject(math)
        advanceUntilIdle()
        assertEquals("30", vm.form.value.price.text)

        // Switching subject keeps a price the user already has — selectSubject only fills an empty field.
        vm.selectSubject(physics)
        advanceUntilIdle()
        assertEquals("30", vm.form.value.price.text)
    }

    @Test
    fun `picking a start time defaults the end one default-lesson later`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        advanceUntilIdle()

        vm.setTimeFrom(LocalTime.of(10, 0))
        assertEquals(LocalTime.of(11, 30), vm.form.value.timeTo)
    }

    @Test
    fun `changing the start time shifts the end to keep the lesson length`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        // A 90-minute lesson, 10:00–11:30.
        vm.setTimeFrom(LocalTime.of(10, 0))
        vm.setTimeTo(LocalTime.of(11, 30))

        // Moving the start later moves the end with it, so it stays 90 minutes (not stale at 11:30).
        vm.setTimeFrom(LocalTime.of(14, 0))
        assertEquals(LocalTime.of(15, 30), vm.form.value.timeTo)
        assertEquals(90, vm.form.value.durationMinutes)
    }

    @Test
    fun `the time pickers open on the current time while nothing is chosen`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        advanceUntilIdle()

        // A lesson is normally written down as it is being arranged, so an empty form offers the
        // current time — to the minute — and one default lesson after it.
        val now = LocalTime.of(14, 37, 42)
        assertEquals(LocalTime.of(14, 37), vm.startPickerDefault(now))
        assertEquals(LocalTime.of(16, 7), vm.endPickerDefault(now))
    }

    @Test
    fun `the time pickers reopen on the times already chosen`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        vm.setTimeFrom(LocalTime.of(10, 0))
        vm.setTimeTo(LocalTime.of(11, 30))

        val now = LocalTime.of(14, 37)
        assertEquals(LocalTime.of(10, 0), vm.startPickerDefault(now))
        assertEquals(LocalTime.of(11, 30), vm.endPickerDefault(now))
    }

    @Test
    fun `editing opens the pickers on the lesson's own time, not the current one`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED",
            ),
        )
        val vm = vm(id)
        advanceUntilIdle()

        val from = start.atZone(zone).toLocalTime()
        assertEquals(from, vm.startPickerDefault(LocalTime.of(14, 37)))
        assertEquals(from.plusMinutes(60), vm.endPickerDefault(LocalTime.of(14, 37)))
    }

    // --- Save: create ---

    @Test
    fun `saving a new class persists it with the chosen fields and signals done`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(3)
        vm.update {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 30),
                price = MoneyField("30"), mode = ClassMode.REMOTE,
            )
        }
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
        ).single()
        return studentId to id
    }

    /** Fills the form for a lesson for [studentId] on [date] from [from] to [to]. */
    private fun ClassEditViewModel.fillFor(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        from: LocalTime,
        to: LocalTime,
    ) = update {
        it.copy(
            studentId = studentId, subjectId = subjectId, date = date,
            timeFrom = from, timeTo = to, price = MoneyField("30"),
        )
    }

    @Test
    fun `a lesson cannot be booked over one that is already there`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        seedLesson(date, LocalTime.of(10, 0)) // Борис, 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Аня at 10:30 would overlap Борис's lesson — the tutor cannot teach both.
        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 30), LocalTime.of(11, 30))
        advanceUntilIdle()

        val clash = vm.state.value.conflict
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(11, 0), LocalTime.of(12, 0))
        advanceUntilIdle()
        assertNull(vm.state.value.conflict)

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
        env.classRepo.setStatus(env.classRepo.getSessionFor(bookedId)!!, ClassStatus.CANCELLED)
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 0), LocalTime.of(11, 0))
        advanceUntilIdle()

        assertNull("a cancelled lesson is not happening, so the slot is free", vm.state.value.conflict)
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `re-saving the lesson being edited does not clash with itself`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        val (_, bookedId) = seedLesson(date, LocalTime.of(10, 0))

        val vm = vm(bookedId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertNull("its own hour is not a conflict", vm.state.value.conflict)

        // Lengthen it in place — the overlap with its own old span must not block the save.
        vm.update { it.copy(timeTo = LocalTime.of(11, 30)) }
        advanceUntilIdle()
        assertNull(vm.state.value.conflict)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(90, env.classDao.items.value.single().durationMinutes)
    }

    @Test
    fun `a group lesson does not clash with its own members when re-saved`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        // A fixed 10:00, not `Instant.now()` plus three days: loading the lesson pre-fills timeTo as
        // timeFrom + 60min, which wraps past midnight when the suite runs late enough in the evening.
        // The form then reads that as a negative range, the save does nothing, and this fails for the
        // time of day rather than for the behaviour (see the cross-midnight test above).
        val start = LocalDate.now(zone).plusDays(3).atTime(10, 0).atZone(zone).toInstant()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Every member's class is excluded, not just the one that was opened.
        assertNull(vm.state.value.conflict)
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
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id,
            start = date.atTime(10, 0).atZone(zone).toInstant(),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )
        val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.fillFor(carl, physics, date, LocalTime.of(10, 30), LocalTime.of(11, 30))
        advanceUntilIdle()

        assertEquals("11 «А»", vm.state.value.conflict?.who)
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
            vm.fillFor(carl, physics, date, LocalTime.of(10, 0), LocalTime.of(11, 0))
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

    @Test
    fun `a group lesson with every member unticked is refused out loud`() =
        runTest(mainRule.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.setTarget(ClassTarget.GROUP)
            vm.selectGroup(groupId)
            advanceUntilIdle()
            vm.setMemberIncluded(ann, included = false)
            vm.setMemberIncluded(bob, included = false)
            val date = LocalDate.now(zone).plusDays(3)
            vm.update {
                it.copy(date = date, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 0))
            }
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
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED",
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.isNew)
        assertEquals(studentId, vm.form.value.studentId)
        assertEquals(subjectId, vm.form.value.subjectId)
        assertEquals("30", vm.form.value.price.text)
    }

    @Test
    fun `editing a completed class into the future re-plans it back to scheduled and voids the charge`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        // A fixed 10:00, for the same reason as the rebill test below: `Instant.now()` minus a day
        // puts timeTo past midnight when the suite runs late, which the form rejects as a negative
        // range, so the save is skipped and the status assertion fails for the clock, not the rule.
        val past = LocalDate.now(zone).minusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Move it to the future and save.
        vm.update { it.copy(date = LocalDate.now(zone).plusDays(2)) }
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
        )
        return Triple(groupId, ann, bob)
    }

    @Test
    fun `a form opened from a group starts out booking that group, in its default mode`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.form.value.target)
        assertEquals(groupId, vm.form.value.groupId)
        assertEquals(ClassMode.REMOTE, vm.form.value.mode)
    }

    @Test
    fun `a group can be booked for any subject, priced for its members or not`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        // Nobody in the group has a Physics price. The lesson bills the group's rate, so that is no
        // longer a reason to withhold the subject.
        env.subjectDao.upsert(SubjectEntity(name = "Физика"))

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Алгебра", "Физика"), vm.state.value.subjects.map { it.name })
    }

    @Test
    fun `choosing a group prefills the lesson's rate from the group`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup(annPrice = Money.ofMajor(30), bobPrice = Money.ofMajor(25))
        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // The group's rate, not either member's individual price — and known from the group alone,
        // without waiting for a subject.
        assertEquals(groupRate, vm.form.value.price.amount)
        assertEquals(2, vm.state.value.members.size)
    }

    @Test
    fun `a rate already typed survives the group's prefill`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setTarget(ClassTarget.GROUP)
        vm.update { it.copy(price = MoneyField("35")) }
        vm.selectGroup(groupId)
        advanceUntilIdle()

        assertEquals(
            "one lesson at an agreed rate isn't overruled by the group's",
            Money.ofMajor(35),
            vm.form.value.price.amount,
        )
    }

    @Test
    fun `an archived member is left out of a new group lesson`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.value.members.map { it.id })

        val date = LocalDate.now(zone).plusDays(2)
        vm.update { it.copy(date = date, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 0)) }
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
            timeTo = LocalTime.of(11, 0),
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.update { it.copy(date = date, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 30)) }
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.update { it.copy(date = date, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 0)) }
        // Bob is away this week. He stays on the roster; he just isn't at this lesson.
        vm.setMemberIncluded(bob, false)
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
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(2)
        vm.update { it.copy(date = date, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 0)) }
        vm.setMemberIncluded(ann, false)
        vm.setMemberIncluded(bob, false)
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
        )

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Only the group's subject is on offer, it is already chosen, and the picker is locked.
        assertEquals(listOf(algebra), vm.state.value.subjects.map { it.id })
        assertEquals(algebra, vm.form.value.subjectId)
        assertTrue(vm.state.value.subjectFixedByGroup)
        assertTrue("physics is not bookable for this group", vm.state.value.subjects.none { it.id == physics })
    }

    @Test
    fun `a group with no subject still offers every subject`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        env.subjectDao.upsert(SubjectEntity(name = "Физика"))

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Pre-v6 groups keep choosing per lesson.
        assertEquals(2, vm.state.value.subjects.size)
        assertFalse(vm.state.value.subjectFixedByGroup)
    }

    @Test
    fun `editing a group lesson loads every member and saves them all`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = Instant.parse("2026-09-01T09:00:00Z")
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )

        // Opening it by *one* member's class must still edit the whole lesson.
        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.form.value.target)
        assertEquals("the rate the lesson was booked at", groupRate, vm.form.value.price.amount)
        assertEquals(listOf(ann, bob), vm.state.value.members.map { it.id })

        // Re-price the lesson and lengthen it; both members' classes must be updated in place.
        vm.update { it.copy(price = MoneyField("28"), timeTo = it.timeFrom!!.plusMinutes(90)) }
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
        val algebra = env.subjectDao.getAll().first().id
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = algebra, start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )
        // Bob leaves the group and a new student joins, *after* the lesson was booked.
        val carl = env.studentDao.upsert(StudentEntity(name = "Вова"))
        env.priceDao.upsert(PriceEntity(carl, algebra, Money.ofMajor(20).minor))
        env.groupRepo.save(Group(id = groupId, name = "11 «А»"), setOf(ann, carl))

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Bob is still listed though he left the group — he is in this lesson and can be taken out
        // of it. Carl is listed because he could be added to it, but unticked because he wasn't there.
        assertEquals(listOf(ann, bob, carl), vm.state.value.members.map { it.id })
        assertEquals(setOf(carl), vm.form.value.excludedMemberIds)
        assertFalse("loading a lesson is not the tutor's edit", vm.state.value.isDirty)

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
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setMemberIncluded(bob, false)
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.classDao.items.value.map { it.studentId })
    }

    @Test
    fun `ticking a group member who was not in a booked lesson adds their class`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann),
            price = groupRate,
        )

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals("Bob is offered, unticked", setOf(bob), vm.form.value.excludedMemberIds)

        vm.setMemberIncluded(bob, true)
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
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setMemberIncluded(ann, false)
        vm.setMemberIncluded(bob, false)
        advanceUntilIdle()

        assertTrue(vm.state.value.allMembersExcluded)
        assertFalse("nothing left to bill", vm.state.value.canSave)
        assertEquals("both classes are still there", 2, env.classDao.items.value.size)
    }

    /** Removing somebody who has already been billed is worth a question, so the form counts them. */
    @Test
    fun `unticking a billed member is counted, an unbilled one is not`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        )
        // Only Ann's class has billed; Bob's was corrected to cancelled.
        val session = env.classRepo.getSessionFor(ids.first())!!
        env.classRepo.setStatus(session, ClassStatus.COMPLETED)
        val bobsClass = env.classRepo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        env.classRepo.setStatus(ClassSession.of(bobsClass), ClassStatus.CANCELLED)

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals("nothing unticked yet", 0, vm.state.value.billedDrops)

        vm.setMemberIncluded(bob, false)
        advanceUntilIdle()
        assertEquals("a cancelled class earned nothing to lose", 0, vm.state.value.billedDrops)

        vm.setMemberIncluded(ann, false)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.billedDrops)
    }

    @Test
    fun `switching the target clears the other target's choices`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setTarget(ClassTarget.GROUP)
        advanceUntilIdle()
        assertEquals(groupId, vm.form.value.groupId) // the only group is auto-selected
        assertNull(vm.form.value.studentId)

        vm.setTarget(ClassTarget.STUDENT)
        advanceUntilIdle()
        assertNull(vm.form.value.groupId)
        assertTrue("the group's rate goes with the group", vm.form.value.price.text.isEmpty())
    }

    @Test
    fun `editing a completed class in the past rebills it from the new price and duration`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        // A fixed 10:00 rather than `Instant.now()` minus a week: the test extends the lesson by 90
        // minutes, and run late enough in the evening that pushed timeTo past midnight, which the
        // form rejects as a negative range (see the cross-midnight test above). The save then did
        // nothing and the charge assertion failed — for the time of day, not for the behaviour.
        val past = LocalDate.now(zone).minusDays(7).atTime(10, 0).atZone(zone).toInstant()
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Keep it in the past (its own date), change price to 40/h and duration to 90 min → charge 60.
        val pastDate = past.atZone(zone).toLocalDate()
        val from = past.atZone(zone).toLocalTime()
        vm.update {
            it.copy(date = pastDate, timeFrom = from, timeTo = from.plusMinutes(90), price = MoneyField("40"))
        }
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("COMPLETED", saved.status)
        assertEquals(Money.ofMajor(60).minor, saved.chargeMinor)
    }
}
