package com.seblful.klas.testutil

import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.people.GroupMemberCrossRef
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDatabaseTest {

    private val database = TestDatabase()

    private val db = database.db

    @AfterTest
    fun closeDatabase() = database.close()

    @Test
    fun `foreign keys are enforced - as on a device`() = runTest {
        val refused = runCatching { db.groupDao().addMember(GroupMemberCrossRef(groupId = 99, studentId = 99)) }
        assertTrue(
            refused.exceptionOrNull()?.message.orEmpty().contains("FOREIGN KEY"),
            "a member of a group that does not exist is refused: ${refused.exceptionOrNull()}",
        )
    }

    @Test
    fun `deleting a lesson cascades to its attendance`() = runTest {
        val subject = db.subjectDao().upsert(SubjectEntity(name = "Алгебра"))
        val student = db.studentDao().upsert(StudentEntity(name = "Аня"))
        val lesson = db.lessonDao().upsert(
            LessonEntity(subjectId = subject, startMillis = 0, durationMinutes = 60, status = LessonStatus.SCHEDULED),
        )
        db.attendanceDao().upsert(AttendanceEntity(lessonId = lesson, studentId = student, state = Attended.EXPECTED))

        db.lessonDao().deleteById(lesson)

        assertEquals(emptyList<Any>(), db.classDao().getAll())
        assertEquals(emptyList<Any>(), db.snapshotDao().read().attendance)
    }

    @Test
    fun `a transaction that fails to commit leaves nothing behind`() = runTest {
        database.transactor.failAt = 1

        val failure = runCatching {
            database.transactor.transaction { db.subjectDao().upsert(SubjectEntity(name = "Алгебра")) }
        }.exceptionOrNull()

        assertEquals("Transaction 1 failed to commit", failure?.message)
        assertEquals(emptyList<SubjectEntity>(), db.subjectDao().getAll())
        assertEquals(0, database.transactor.depth, "nothing left open")
    }

    @Test
    fun `an open query has re-read by the time a write returns`() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch { db.subjectDao().observeAll().collect { rows -> seen += rows.map { it.name } } }

        db.subjectDao().upsert(SubjectEntity(name = "Алгебра"))

        assertEquals(listOf(emptyList(), listOf("Алгебра")), seen)
        assertEquals(listOf("Алгебра"), db.subjectDao().observeAll().first().map { it.name })
    }
}
