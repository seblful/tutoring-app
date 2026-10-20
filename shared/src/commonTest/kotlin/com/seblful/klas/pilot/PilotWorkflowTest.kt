package com.seblful.klas.pilot

import com.seblful.klas.testutil.DatabaseFile
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PilotWorkflowTest {
    private val env = TestEnv()

    @AfterTest
    fun close() = env.close()

    @Test
    fun studentsAndBookingsSurviveClosingAndReopeningTheProductionStore() = runTest {
        DatabaseFile().use { file ->
            val saved = file.open().use { database ->
                val pilot = PilotWorkflow(env.store(database))
                val student = pilot.addStudent(" Ann ").students.single()
                pilot.book(student.id, env.clock.now().toEpochMilliseconds())
            }

            file.open().use { database ->
                val restored = PilotWorkflow(env.store(database)).load()
                assertEquals(saved, restored)
                assertEquals("Ann", restored.students.single().name)
                assertEquals("Ann", restored.lessons.single().studentName)
            }
        }
    }

    @Test
    fun bookingAnOccupiedHourReportsTheRefusalAndKeepsTheOriginalLesson() = runTest {
        val pilot = PilotWorkflow(env.store())
        val student = pilot.addStudent("Ann").students.single()
        val start = env.clock.now().toEpochMilliseconds()
        val before = pilot.book(student.id, start)

        val failure = assertFailsWith<IllegalStateException> { pilot.book(student.id, start) }

        assertTrue(failure.message.orEmpty().contains("already booked"))
        assertEquals(before, pilot.load())
    }

    @Test
    fun anEmptyNameDoesNotCreateAStudentOrSubject() = runTest {
        val pilot = PilotWorkflow(env.store())
        assertFailsWith<IllegalArgumentException> { pilot.addStudent("  ") }
        assertTrue(pilot.load().students.isEmpty())
        assertTrue(env.stored().subjects.isEmpty())
    }
}
