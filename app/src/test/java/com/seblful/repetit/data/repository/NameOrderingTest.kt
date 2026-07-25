package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Name ordering as the UI receives it. The fake DAOs return rows unordered on purpose, so what these
 * assert is the repositories' own use of `format/NameOrder` — the single place the order is decided,
 * on the device and here alike.
 */
class NameOrderingTest {

    private val env = TestEnv()

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
        // «Ярослав» before «анна» is what the old SQL collation produced; it must not any more.
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
