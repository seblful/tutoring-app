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

    private fun vm() = SettingsViewModel(env.settingsRepo, env.classRepo)

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
        vm.setCalendarSyncEnabled(true)
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(1, env.calendar.upserts.size) // the scheduled class got mirrored
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
