package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ClassRepositoryRecentTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()
    private val repo = env.classRepo

    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private var ann = 0L
    private var bob = 0L
    private var maths = 0L
    private var groupId = 0L

    private suspend fun seed() {
        ann = env.fixtures.student("Аня")
        bob = env.fixtures.student("Борис")
        maths = env.fixtures.subject("Математика")
        groupId = env.fixtures.group("9 «Б»", members = setOf(ann, bob))
    }

    private suspend fun book(
        at: Instant,
        students: List<Long>,
        group: Long? = null,
        minutes: Int = 60,
        mode: ClassMode = ClassMode.IN_PERSON,
    ): Long = env.fixtures.lesson(maths, students, at, minutes, groupId = group, mode = mode).first()

    @Test
    fun `recently taught puts the latest lesson first and skips future and cancelled ones`() = runTest {
        seed()
        book(now - 3.days, listOf(ann))
        book(now - 1.days, listOf(ann, bob), group = groupId)
        // Bob's own lesson is later than the group's, but it was cancelled.
        env.fixtures.cancelledLesson(maths, listOf(bob), now - 2.hours)
        // Booked, not yet taught.
        book(now + 1.days, listOf(bob))

        val recent = repo.observeRecentlyTaught(now, limit = 5).first()

        assertEquals(listOf(Assignee.Group(groupId), Assignee.Student(ann)), recent)
    }

    @Test
    fun `a student taught only in a group is not recent on their own`() = runTest {
        seed()
        book(now - 1.days, listOf(ann, bob), group = groupId)

        assertEquals(listOf(Assignee.Group(groupId)), repo.observeRecentlyTaught(now, limit = 5).first())
    }

    @Test
    fun `recently taught stops at the limit`() = runTest {
        seed()
        book(now - 3.days, listOf(ann))
        book(now - 2.days, listOf(bob))
        book(now - 1.days, listOf(ann, bob), group = groupId)

        assertEquals(
            listOf(Assignee.Group(groupId), Assignee.Student(bob)),
            repo.observeRecentlyTaught(now, limit = 2).first(),
        )
    }
}
