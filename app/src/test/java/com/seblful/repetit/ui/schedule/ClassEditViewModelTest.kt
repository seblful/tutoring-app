package com.seblful.repetit.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
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

    private fun vm(id: Long = 0L) = ClassEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        classes = env.classRepo,
        studentsRepo = env.studentRepo,
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
