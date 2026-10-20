package com.seblful.klas.data.repository

import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NameOrderingTest {

    @get:Rule
    val env = TestEnv()

    @Test
    fun `subjects come out in reader order, whatever order they were added in`() = runTest {
        listOf("Физика", "алгебра", "Биология").forEach { env.subjectDao.upsert(SubjectEntity(name = it)) }

        assertEquals(
            listOf("алгебра", "Биология", "Физика"),
            env.subjectRepo.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `active students are ordered case-insensitively, including Cyrillic`() = runTest {
        listOf("Ярослав", "анна", "Борис").forEach { env.studentDao.upsert(StudentEntity(name = it)) }

        assertEquals(
            listOf("анна", "Борис", "Ярослав"),
            env.studentRepo.observeActive().first().map { it.name },
        )
    }

    @Test
    fun `archived students are listed last, each group in reader order`() = runTest {
        env.studentDao.upsert(StudentEntity(name = "Ярослав"))
        env.studentDao.upsert(StudentEntity(name = "анна", archived = true))
        env.studentDao.upsert(StudentEntity(name = "Борис"))

        assertEquals(
            listOf("Борис", "Ярослав", "анна"),
            env.studentRepo.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `active groups are ordered, and archived ones left out`() = runTest {
        env.groupDao.upsert(GroupEntity(name = "Ярославцы"))
        env.groupDao.upsert(GroupEntity(name = "абитуриенты"))
        env.groupDao.upsert(GroupEntity(name = "Выпускники", archived = true))

        assertEquals(
            listOf("абитуриенты", "Ярославцы"),
            env.groupRepo.observeActive().first().map { it.name },
        )
    }

    @Test
    fun `archived groups are listed last by observeAll`() = runTest {
        env.groupDao.upsert(GroupEntity(name = "Ярославцы"))
        env.groupDao.upsert(GroupEntity(name = "абитуриенты", archived = true))
        env.groupDao.upsert(GroupEntity(name = "Выпускники"))

        assertEquals(
            listOf("Выпускники", "Ярославцы", "абитуриенты"),
            env.groupRepo.observeAll().first().map { it.name },
        )
    }
}
