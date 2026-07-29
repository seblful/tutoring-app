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

/**
 * The bar above «Люди» works one [PeopleSide]; the two view models are its adapters. What the seam
 * promises is exactly what having two view models is *for*: each side holds its own search and its
 * own archive toggle, so acting on the side on top leaves the other untouched — switching tabs
 * brings a side back exactly as it was left. The screen used to dispatch this by hand, per button,
 * where no test could flip the tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleSideTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    @Test
    fun `each side holds its own search and archive toggle`() = runTest(mainRule.dispatcher) {
        val students: PeopleSide =
            StudentsViewModel(env.studentRepo, env.classRepo, env.paymentRepo, mainRule.dispatcher)
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
            StudentsViewModel(env.studentRepo, env.classRepo, env.paymentRepo, mainRule.dispatcher)
        val groups: PeopleSide =
            GroupsViewModel(env.groupRepo, env.studentRepo, env.subjectRepo, mainRule.dispatcher)
        students.setQuery("пет")
        groups.setQuery("11")

        // What the bar's close button does — see PeopleScreen: a filter left applied behind a
        // closed box is a list missing rows for no visible reason.
        students.setQuery("")

        assertEquals("", students.query.value)
        assertEquals("11", groups.query.value)
    }
}
