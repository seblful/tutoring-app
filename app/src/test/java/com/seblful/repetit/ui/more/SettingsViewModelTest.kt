package com.seblful.repetit.ui.more

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = SettingsViewModel(env.settingsRepo, env.classRepo, env.calendarPermission)

    @Test
    fun `setters persist each setting`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setReminderLeadMinutes(30)
        vm.setAutoCompleteEnabled(true)
        vm.setThemeMode("DARK")
        vm.setDefaultLessonMinutes(45)
        vm.setCurrency("RUB")
        vm.setNotificationsEnabled(false)
        advanceUntilIdle()

        val s = env.settingsRepo.get()
        assertEquals(30, s.reminderLeadMinutes)
        assertTrue(s.autoCompleteEnabled)
        assertEquals("DARK", s.themeMode)
        assertEquals(45, s.defaultLessonMinutes)
        assertEquals("RUB", s.currency)
        assertFalse(s.notificationsEnabled)
    }

    @Test
    fun `enabling calendar sync backfills events for existing classes`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))

        val vm = vm()
        assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(1, env.calendar.upserts.size) // the scheduled class got mirrored
    }

    @Test
    fun `enabling calendar sync without the permission changes nothing`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))
        env.calendarPermission.granted = false

        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        // The flag the whole mirror keys off stays off, and nothing was written.
        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertTrue(env.calendar.upserts.isEmpty())
    }

    @Test
    fun `enabling succeeds once the permission is granted`() = runTest(mainRule.dispatcher) {
        env.calendarPermission.granted = false
        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))

        env.calendarPermission.granted = true
        assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
    }

    @Test
    fun `disabling calendar sync never needs the permission — clearing up must always work`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.classDao.upsert(
                ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-9"),
            )
            env.settingsRepo.setCalendarSyncEnabled(true)
            env.calendarPermission.granted = false

            val vm = vm()
            assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(false))
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().calendarSyncEnabled)
            assertEquals(listOf("evt-9"), env.calendar.deletes)
        }

    @Test
    fun `disabling calendar sync clears the events we created`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // A class already carrying a mirrored event id.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-1"))
        env.settingsRepo.setCalendarSyncEnabled(true)

        val vm = vm()
        vm.setCalendarSyncEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(listOf("evt-1"), env.calendar.deletes)
        // The event id is cleared off the class.
        assertEquals(null, env.classDao.items.value.single().calendarEventId)
    }
}
