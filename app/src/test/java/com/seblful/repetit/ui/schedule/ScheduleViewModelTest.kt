package com.seblful.repetit.ui.schedule

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun vm() = ScheduleViewModel(env.classRepo, env.studentRepo, env.subjectRepo)

    private fun startMillisOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `add is disabled until at least one student and one subject exist`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.canAddClass.collect {} }
        advanceUntilIdle()
        assertFalse(vm.canAddClass.value)

        env.studentDao.upsert(StudentEntity(name = "Аня"))
        advanceUntilIdle()
        assertFalse("a student without any subject still can't book", vm.canAddClass.value)

        env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        advanceUntilIdle()
        assertTrue(vm.canAddClass.value)
    }

    @Test
    fun `rows show only the selected day's classes, time-sorted, with student and subject names`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val today = LocalDate.now(zone)
        // Two classes today (out of order) and one tomorrow.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(15, 0)), durationMinutes = 60, status = "SCHEDULED"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today.plusDays(1), LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))

        val vm = vm()
        backgroundScope.launch { vm.rows.collect {} }
        advanceUntilIdle()

        val rows = vm.rows.value
        assertEquals(2, rows.size)
        assertTrue("rows are sorted by start time", rows[0].klass.start < rows[1].klass.start)
        assertEquals("Аня", rows[0].studentName)
        assertEquals("Математика", rows[0].subjectName)
    }

    @Test
    fun `day navigation moves the selected date and today resets it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val today = LocalDate.now(zone)

        vm.goToNextDay()
        assertEquals(today.plusDays(1), vm.selectedDate.value)
        vm.goToPreviousDay()
        vm.goToPreviousDay()
        assertEquals(today.minusDays(1), vm.selectedDate.value)
        vm.goToToday()
        assertEquals(today, vm.selectedDate.value)
    }

    @Test
    fun `selecting a day repoints the list and brings the calendar page with it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val target = LocalDate.of(2026, 11, 20)

        vm.selectDate(target)

        assertEquals(target, vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 11), vm.visibleMonth.value)
    }

    @Test
    fun `day navigation carries the calendar page across a month boundary`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 30))

        vm.goToNextDay()

        assertEquals(LocalDate.of(2026, 12, 1), vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 12), vm.visibleMonth.value)
    }

    @Test
    fun `paging months browses without moving the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 20))

        vm.goToNextMonth()
        assertEquals(YearMonth.of(2026, 12), vm.visibleMonth.value)
        vm.goToPreviousMonth()
        vm.goToPreviousMonth()
        assertEquals(YearMonth.of(2026, 10), vm.visibleMonth.value)
        assertEquals("browsing must not repoint the day list", LocalDate.of(2026, 11, 20), vm.selectedDate.value)
    }

    @Test
    fun `opening the calendar snaps the page back to the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 20))
        vm.goToNextMonth() // browsed away, then closed the calendar

        vm.toggleViewMode()

        assertEquals(ScheduleViewMode.MONTH, vm.viewMode.value)
        assertEquals(YearMonth.of(2026, 11), vm.visibleMonth.value)

        vm.toggleViewMode()
        assertEquals(ScheduleViewMode.DAY, vm.viewMode.value)
    }

    @Test
    fun `the calendar grid follows the visible month`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.monthGrid.collect {} }
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 7), vm.monthGrid.value.month)
        assertTrue(LocalDate.of(2026, 7, 15) in vm.monthGrid.value.weeks.flatten())
    }

    @Test
    fun `day markers cover the whole page, padding days included`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // July 2026 starts on a Wednesday, so 30 June is a padding cell on the first row.
        val padding = LocalDate.of(2026, 6, 30)
        val inMonth = LocalDate.of(2026, 7, 15)
        val otherMonth = LocalDate.of(2026, 9, 1)
        listOf(padding to LocalTime.of(9, 0), inMonth to LocalTime.of(9, 0), inMonth to LocalTime.of(15, 0))
            .forEach { (day, time) ->
                env.classDao.upsert(
                    ClassEntity(
                        studentId = sid, subjectId = subj, startMillis = startMillisOn(day, time),
                        durationMinutes = 60, status = "SCHEDULED",
                    )
                )
            }
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(otherMonth, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "CANCELLED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.dayLoads.collect {} }
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()

        val loads = vm.dayLoads.value
        assertEquals(2, loads.getValue(inMonth).total)
        assertEquals("padding cells are marked too", 1, loads.getValue(padding).total)
        assertFalse("days outside the page are not loaded", otherMonth in loads)
    }

    @Test
    fun `paging to another month reloads its markers`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val august = LocalDate.of(2026, 8, 12)
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(august, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "COMPLETED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.dayLoads.collect {} }
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()
        assertFalse(august in vm.dayLoads.value)

        vm.goToNextMonth()
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.value.getValue(august).total)
        assertEquals(setOf(ClassStatus.COMPLETED), vm.dayLoads.value.getValue(august).statuses)
    }

    @Test
    fun `setStatus completes a class and captures its charge`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 90, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED"))

        val vm = vm()
        vm.setStatus(id, ClassStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("COMPLETED", saved.status)
        assertEquals(Money.ofMajor(45).minor, saved.chargeMinor) // 30/h * 90 min
    }

    @Test
    fun `delete removes the class`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "CANCELLED"))

        val vm = vm()
        vm.delete(env.classRepo.getById(id)!!)
        advanceUntilIdle()

        assertTrue(env.classDao.items.value.isEmpty())
    }
}
