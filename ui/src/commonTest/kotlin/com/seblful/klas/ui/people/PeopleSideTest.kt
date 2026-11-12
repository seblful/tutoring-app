package com.seblful.klas.ui.people

import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.ui.groups.GroupsViewModel
import com.seblful.klas.ui.students.StudentsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleSideTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    private val main = TestMain()

    private val env = UiTestEnv()

    @Test
    fun `each side holds its own search and archive toggle`() = runTest(main.dispatcher) {
        val students: PeopleSide = main.own(
            StudentsViewModel(
                env.studentRepo, env.debts, env.classRepo, env.enrollmentRepo, env.subjectRepo, env.clock,
                main.dispatcher,
            ),
        )
        val groups: PeopleSide = main.own(
            GroupsViewModel(
                env.groupRepo, env.studentRepo, env.subjectRepo, env.classRepo, env.clock, main.dispatcher,
            ),
        )

        students.setQuery("пет")
        groups.setQuery("11")
        students.toggleArchived()

        assertEquals("пет", students.query.value)
        assertEquals("11", groups.query.value, "the groups side kept its own search")
        assertTrue(students.showArchived.value)
        assertFalse(groups.showArchived.value, "the archive toggle is per side too")
    }

    @Test
    fun `closing the search clears only the side it was open on`() = runTest(main.dispatcher) {
        val students: PeopleSide = main.own(
            StudentsViewModel(
                env.studentRepo, env.debts, env.classRepo, env.enrollmentRepo, env.subjectRepo, env.clock,
                main.dispatcher,
            ),
        )
        val groups: PeopleSide = main.own(
            GroupsViewModel(
                env.groupRepo, env.studentRepo, env.subjectRepo, env.classRepo, env.clock, main.dispatcher,
            ),
        )
        students.setQuery("пет")
        groups.setQuery("11")

        students.setQuery("")

        assertEquals("", students.query.value)
        assertEquals("11", groups.query.value)
    }
}
