package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NameOrderingTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()

    @Test
    fun `subjects come out in reader order - whatever order they were added in`() = runTest {
        listOf("Физика", "алгебра", "Биология").forEach { env.fixtures.subject(name = it) }

        assertEquals(
            listOf("алгебра", "Биология", "Физика"),
            env.subjectRepo.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `active students are ordered case-insensitively - including Cyrillic`() = runTest {
        listOf("Ярослав", "анна", "Борис").forEach { env.fixtures.student(name = it) }

        assertEquals(
            listOf("анна", "Борис", "Ярослав"),
            env.studentRepo.observeActive().first().map { it.name },
        )
    }

    @Test
    fun `archived students are listed last - each group in reader order`() = runTest {
        env.fixtures.student(name = "Ярослав")
        env.fixtures.student(name = "анна", archived = true)
        env.fixtures.student(name = "Борис")

        assertEquals(
            listOf("Борис", "Ярослав", "анна"),
            env.studentRepo.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `active groups are ordered - and archived ones left out`() = runTest {
        env.fixtures.group(name = "Ярославцы", rate = Money.ZERO)
        env.fixtures.group(name = "абитуриенты", rate = Money.ZERO)
        env.fixtures.group(name = "Выпускники", archived = true, rate = Money.ZERO)

        assertEquals(
            listOf("абитуриенты", "Ярославцы"),
            env.groupRepo.observeActive().first().map { it.name },
        )
    }

    @Test
    fun `archived groups are listed last by observeAll`() = runTest {
        env.fixtures.group(name = "Ярославцы", rate = Money.ZERO)
        env.fixtures.group(name = "абитуриенты", archived = true, rate = Money.ZERO)
        env.fixtures.group(name = "Выпускники", rate = Money.ZERO)

        assertEquals(
            listOf("Выпускники", "Ярославцы", "абитуриенты"),
            env.groupRepo.observeAll().first().map { it.name },
        )
    }
}
