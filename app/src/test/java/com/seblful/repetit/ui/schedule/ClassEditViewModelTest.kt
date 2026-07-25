package com.seblful.repetit.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        pricesRepo = env.priceRepo,
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
            priceText = "30",
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
            priceText = "30",
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
            priceText = "30",
        )
        assertNull(crossingMidnight.durationMinutes)
        assertFalse(crossingMidnight.isValid)
    }

    // --- Auto-selection on a new class ---

    @Test
    fun `a new class auto-selects the only student and their only priced subject and prefills the price`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject(price = Money.ofMajor(30))
        val vm = vm()
        backgroundScope.launch { vm.students.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertEquals(studentId, vm.form.value.studentId)
        assertEquals(subjectId, vm.form.value.subjectId)
        assertEquals("30", vm.form.value.priceText)
    }

    @Test
    fun `with several students none is auto-selected`() = runTest(mainRule.dispatcher) {
        env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Борис"))
        val vm = vm()
        backgroundScope.launch { vm.students.collect {} }
        advanceUntilIdle()

        assertNull(vm.form.value.studentId)
    }

    @Test
    fun `selecting a different student clears the subject and price and adopts the student's mode`() = runTest(mainRule.dispatcher) {
        // Two students, so nothing is auto-selected and selectStudent runs its clearing logic.
        env.studentDao.upsert(StudentEntity(name = "Аня", defaultMode = ClassMode.IN_PERSON.name))
        val remoteId = env.studentDao.upsert(StudentEntity(name = "Борис", defaultMode = ClassMode.REMOTE.name))
        val vm = vm()
        backgroundScope.launch { vm.students.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        vm.update { it.copy(subjectId = 99, priceText = "50") }
        vm.selectStudent(remoteId)
        advanceUntilIdle()

        assertEquals(remoteId, vm.form.value.studentId)
        assertNull(vm.form.value.subjectId)
        assertEquals("", vm.form.value.priceText)
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
        backgroundScope.launch { vm.students.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        // Two students? No — one student, two subjects, so nothing auto-selected.
        vm.selectStudent(studentId)
        advanceUntilIdle()
        vm.selectSubject(math)
        advanceUntilIdle()
        assertEquals("30", vm.form.value.priceText)

        // Switching subject keeps a price the user already has — selectSubject only fills an empty field.
        vm.selectSubject(physics)
        advanceUntilIdle()
        assertEquals("30", vm.form.value.priceText)
    }

    @Test
    fun `picking a start time defaults the end one default-lesson later`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.setDefaultLessonMinutes(90)
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
        env.settingsRepo.setDefaultLessonMinutes(90)
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
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        val date = LocalDate.now(zone).plusDays(3)
        vm.update {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(11, 30),
                priceText = "30", mode = ClassMode.REMOTE,
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
            pricesByStudent = mapOf(studentId to Money.ofMajor(30)),
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
            timeFrom = from, timeTo = to, priceText = "30",
        )
    }

    @Test
    fun `a lesson cannot be booked over one that is already there`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        seedLesson(date, LocalTime.of(10, 0)) // Борис, 10:00–11:00
        val (annId, subjectId) = seedStudentWithPricedSubject(studentName = "Аня", subjectName = "Физика")

        val vm = vm()
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        // Аня at 10:30 would overlap Борис's lesson — the tutor cannot teach both.
        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 30), LocalTime.of(11, 30))
        advanceUntilIdle()

        val clash = vm.conflict.value
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
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(11, 0), LocalTime.of(12, 0))
        advanceUntilIdle()
        assertNull(vm.conflict.value)

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
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        vm.fillFor(annId, subjectId, date, LocalTime.of(10, 0), LocalTime.of(11, 0))
        advanceUntilIdle()

        assertNull("a cancelled lesson is not happening, so the slot is free", vm.conflict.value)
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `re-saving the lesson being edited does not clash with itself`() = runTest(mainRule.dispatcher) {
        val date = LocalDate.now(zone).plusDays(3)
        val (_, bookedId) = seedLesson(date, LocalTime.of(10, 0))

        val vm = vm(bookedId)
        backgroundScope.launch { vm.subjects.collect {} }
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        assertNull("its own hour is not a conflict", vm.conflict.value)

        // Lengthen it in place — the overlap with its own old span must not block the save.
        vm.update { it.copy(timeTo = LocalTime.of(11, 30)) }
        advanceUntilIdle()
        assertNull(vm.conflict.value)

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(90, env.classDao.items.value.single().durationMinutes)
    }

    @Test
    fun `a group lesson does not clash with its own members when re-saved`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = Instant.now().plusSeconds(3 * 86_400)
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            pricesByStudent = mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
        )

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        // Every member's class is excluded, not just the one that was opened.
        assertNull(vm.conflict.value)
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
            pricesByStudent = mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
        )
        val (carl, physics) = seedStudentWithPricedSubject(studentName = "Вова", subjectName = "Физика")

        val vm = vm()
        backgroundScope.launch { vm.conflict.collect {} }
        advanceUntilIdle()

        vm.fillFor(carl, physics, date, LocalTime.of(10, 30), LocalTime.of(11, 30))
        advanceUntilIdle()

        assertEquals("11 «А»", vm.conflict.value?.who)
        vm.save { }
        advanceUntilIdle()
        assertEquals("no third class was written", 2, env.classDao.items.value.size)
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
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertFalse(vm.isNew)
        assertEquals(studentId, vm.form.value.studentId)
        assertEquals(subjectId, vm.form.value.subjectId)
        assertEquals("30", vm.form.value.priceText)
    }

    @Test
    fun `editing a completed class into the future re-plans it back to scheduled and voids the charge`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val past = Instant.now().minusSeconds(86_400)
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.subjects.collect {} }
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

    /** Two students in one group, both priced for the same subject (so it is bookable). */
    private suspend fun seedGroup(
        annPrice: Money = Money.ofMajor(30),
        bobPrice: Money = Money.ofMajor(25),
    ): Triple<Long, Long, Long> {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.priceDao.upsert(PriceEntity(ann, algebra, annPrice.minor))
        env.priceDao.upsert(PriceEntity(bob, algebra, bobPrice.minor))
        val groupId = env.groupRepo.save(Group(name = "11 «А»", defaultMode = ClassMode.REMOTE), setOf(ann, bob))
        return Triple(groupId, ann, bob)
    }

    @Test
    fun `a form opened from a group starts out booking that group, in its default mode`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.form.value.target)
        assertEquals(groupId, vm.form.value.groupId)
        assertEquals(ClassMode.REMOTE, vm.form.value.mode)
    }

    @Test
    fun `a group can only be booked for a subject every member is priced for`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, _) = seedGroup()
        // Ann alone is priced for Physics, so it must not be offered for the group.
        val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        env.priceDao.upsert(PriceEntity(ann, physics, Money.ofMajor(50).minor))

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Алгебра"), vm.subjects.value.map { it.name })
    }

    @Test
    fun `choosing a subject prefills every member's own rate`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup(annPrice = Money.ofMajor(30), bobPrice = Money.ofMajor(25))
        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        // The group's only bookable subject is auto-selected, which prefills the rates.
        assertEquals(mapOf(ann to "30", bob to "25"), vm.form.value.memberPrices)
        assertEquals(2, vm.members.value.size)
    }

    @Test
    fun `an archived member is left out of a new group lesson`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.members.value.map { it.id })
        assertEquals(setOf(ann), vm.form.value.memberPrices.keys)
    }

    @Test
    fun `the group form is invalid until every member has a valid rate`() {
        val base = ClassForm(
            target = ClassTarget.GROUP,
            groupId = 7, subjectId = 1,
            date = LocalDate.of(2026, 9, 1),
            timeFrom = LocalTime.of(10, 0),
            timeTo = LocalTime.of(11, 0),
        )
        assertFalse("no members, nothing to bill", base.isValid)
        assertFalse(base.copy(memberPrices = mapOf(1L to "30", 2L to "")).isValid)
        assertFalse(base.copy(memberPrices = mapOf(1L to "30", 2L to "abc")).isValid)
        assertTrue(base.copy(memberPrices = mapOf(1L to "30", 2L to "25,50")).isValid)
    }

    @Test
    fun `saving a new group lesson writes one class per member at their own rate`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
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
            mapOf(ann to Money.ofMajor(30).minor, bob to Money.ofMajor(25).minor),
            saved.associate { it.studentId to it.priceMinor },
        )
    }

    @Test
    fun `editing a group lesson loads every member and saves them all`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = Instant.parse("2026-09-01T09:00:00Z")
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.subjectDao.getAll().first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            pricesByStudent = mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
        )

        // Opening it by *one* member's class must still edit the whole lesson.
        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.form.value.target)
        assertEquals(mapOf(ann to "30", bob to "25"), vm.form.value.memberPrices)
        assertEquals(listOf(ann, bob), vm.members.value.map { it.id })

        // Raise Bob's rate and lengthen the lesson; both members' classes must be updated in place.
        vm.setMemberPrice(bob, "28")
        vm.update { it.copy(timeTo = it.timeFrom!!.plusMinutes(90)) }
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.durationMinutes == 90 })
        assertEquals(Money.ofMajor(28).minor, saved.single { it.studentId == bob }.priceMinor)
        assertEquals(Money.ofMajor(30).minor, saved.single { it.studentId == ann }.priceMinor)
    }

    @Test
    fun `editing an old lesson keeps its own members after the roster changed`() = runTest(mainRule.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val algebra = env.subjectDao.getAll().first().id
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = algebra, start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            pricesByStudent = mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
        )
        // Bob leaves the group and a new student joins, *after* the lesson was booked.
        val carl = env.studentDao.upsert(StudentEntity(name = "Вова"))
        env.priceDao.upsert(PriceEntity(carl, algebra, Money.ofMajor(20).minor))
        env.groupRepo.save(Group(id = groupId, name = "11 «А»"), setOf(ann, carl))

        val vm = vm(id = ids.first())
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.members.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        // The lesson still reads as the lesson it was: Ann and Bob, not Ann and Carl.
        assertEquals(listOf(ann, bob), vm.members.value.map { it.id })
        assertEquals(setOf(ann, bob), vm.form.value.memberPrices.keys)

        // Re-saving must not create a class for the newcomer, nor drop the departed member's.
        vm.save { }
        advanceUntilIdle()
        assertEquals(2, env.classDao.items.value.size)
        assertEquals(setOf(ann, bob), env.classDao.items.value.map { it.studentId }.toSet())
    }

    @Test
    fun `switching the target clears the other target's choices`() = runTest(mainRule.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        backgroundScope.launch { vm.students.collect {} }
        backgroundScope.launch { vm.groups.collect {} }
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        vm.setTarget(ClassTarget.GROUP)
        advanceUntilIdle()
        assertEquals(groupId, vm.form.value.groupId) // the only group is auto-selected
        assertNull(vm.form.value.studentId)

        vm.setTarget(ClassTarget.STUDENT)
        advanceUntilIdle()
        assertNull(vm.form.value.groupId)
        assertTrue(vm.form.value.memberPrices.isEmpty())
    }

    @Test
    fun `editing a completed class in the past rebills it from the new price and duration`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val past = Instant.now().minusSeconds(7 * 86_400)
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = studentId, subjectId = subjectId, startMillis = past.toEpochMilli(),
                durationMinutes = 60, priceMinor = Money.ofMajor(30).minor,
                status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor,
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.subjects.collect {} }
        advanceUntilIdle()

        // Keep it in the past (its own date), change price to 40/h and duration to 90 min → charge 60.
        val pastDate = past.atZone(zone).toLocalDate()
        val from = past.atZone(zone).toLocalTime()
        vm.update {
            it.copy(date = pastDate, timeFrom = from, timeTo = from.plusMinutes(90), priceText = "40")
        }
        vm.save { }
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("COMPLETED", saved.status)
        assertEquals(Money.ofMajor(60).minor, saved.chargeMinor)
    }
}
