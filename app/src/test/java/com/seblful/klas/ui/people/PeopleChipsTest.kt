package com.seblful.klas.ui.people

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.groups.GroupSort
import com.seblful.klas.ui.groups.SubjectChoice
import com.seblful.klas.ui.students.StudentSort
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PeopleChipsTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val asked = mutableListOf<Any?>()

    @Test
    fun `students narrow to debtors and the archive from chips, and order from a third`() {
        compose.setContent {
            KlasTheme {
                StudentChips(
                    owing = 3, owesOnly = true, onOwes = { asked += "owes" },
                    archived = false, onArchived = { asked += "archived" },
                    sort = StudentSort.NAME, onSort = { asked += it },
                )
            }
        }

        compose.onNodeWithText("Owes · 3").assertIsSelected().performClick()
        compose.onNodeWithText("Archived").performClick()
        compose.onNodeWithText("Sort: By name").performClick()
        compose.onNodeWithText("Debtors first").performClick()

        assertEquals(listOf("owes", "archived", StudentSort.DEBT), asked)
    }

    @Test
    fun `groups get a subject chip once they are taught two, beside the archive and the order`() {
        compose.setContent {
            KlasTheme {
                GroupChips(
                    subjects = listOf(SubjectChoice(1, "Math"), SubjectChoice(2, "Physics")),
                    subject = null, onSubject = { asked += it },
                    archived = false, onArchived = {},
                    sort = GroupSort.NAME, onSort = {},
                )
            }
        }

        compose.onNodeWithText("All subjects").performClick()
        compose.onNodeWithText("Physics").performClick()

        assertEquals(listOf<Any?>(2L), asked)
    }
}
