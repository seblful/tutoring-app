package com.seblful.tutoringapp.ui.schedule

import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.testutil.MainDispatcherRule
import com.seblful.tutoringapp.testutil.TestEnv
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
