package com.seblful.klas.ui.people

import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.groups.GroupsViewModel
import com.seblful.klas.ui.students.StudentsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PeopleSideTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    @Test
    fun `each side holds its own search and archive toggle`() = runTest(mainRule.dispatcher) {
        val students: PeopleSide =
            StudentsViewModel(env.studentRepo, env.balanceRepo, mainRule.dispatcher)
        val groups: PeopleSide =
            GroupsViewModel(env.groupRepo, env.studentRepo, env.subjectRepo, mainRule.dispatcher)

        students.setQuery("пет")
        groups.setQuery("11")
        students.toggleArchived()

        assertEquals("пет", students.query.value)
        assertEquals("the groups side kept its own search", "11", groups.query.value)
        assertTrue(students.showArchived.value)
        assertFalse("the archive toggle is per side too", groups.showArchived.value)
    }

    @Test
    fun `closing the search clears only the side it was open on`() = runTest(mainRule.dispatcher) {
        val students: PeopleSide =
            StudentsViewModel(env.studentRepo, env.balanceRepo, mainRule.dispatcher)
        val groups: PeopleSide =
            GroupsViewModel(env.groupRepo, env.studentRepo, env.subjectRepo, mainRule.dispatcher)
        students.setQuery("пет")
        groups.setQuery("11")

        students.setQuery("")

        assertEquals("", students.query.value)
        assertEquals("11", groups.query.value)
    }
}
