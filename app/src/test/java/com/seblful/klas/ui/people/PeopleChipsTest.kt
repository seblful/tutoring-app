package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

    private fun menu() = compose.onNodeWithContentDescription("Filter and order").performClick()

    @Test
    fun `the students' menu narrows to debtors and the archive, and orders them`() {
        compose.setContent {
            KlasTheme {
                StudentFilterAction(
                    owing = 3, owesOnly = false, onOwes = { asked += "owes" },
                    archived = false, onArchived = { asked += "archived" },
                    sort = StudentSort.NAME, onSort = { asked += it },
                )
            }
        }

        menu()
        compose.onNodeWithText("Owes · 3").performClick()
        menu()
        compose.onNodeWithText("Archived").performClick()
        menu()
        compose.onNodeWithText("Debtors first").performClick()

        assertEquals(listOf("owes", "archived", StudentSort.DEBT), asked)
    }

    @Test
    fun `nobody owing, the menu does not offer debtors`() {
        compose.setContent {
            KlasTheme {
                StudentFilterAction(
                    owing = 0, owesOnly = false, onOwes = {},
                    archived = false, onArchived = {},
                    sort = StudentSort.NAME, onSort = {},
                )
            }
        }

        menu()
        compose.onNodeWithText("Owes · 0").assertDoesNotExist()
    }

    @Test
    fun `the students' narrowings in force are chips that take them off, and none draws nothing`() {
        compose.setContent {
            KlasTheme {
                StudentFilters(
                    owing = 3, owesOnly = true, onOwes = { asked += "owes" },
                    archived = false, onArchived = { asked += "archived" },
                )
            }
        }

        compose.onNodeWithText("Owes · 3").performClick()
        compose.onNodeWithText("Archived").assertDoesNotExist()

        assertEquals(listOf<Any?>("owes"), asked)
    }

    @Test
    fun `groups get a subject in the menu once they are taught two, and its chip names it`() {
        val subjects = listOf(SubjectChoice(1, "Math"), SubjectChoice(2, "Physics"))
        compose.setContent {
            KlasTheme {
                Column {
                    GroupFilterAction(
                        subjects = subjects,
                        subject = null, onSubject = { asked += it },
                        archived = false, onArchived = {},
                        sort = GroupSort.NAME, onSort = {},
                    )
                    GroupFilters(subjects, subject = 1, onSubject = { asked += it }, archived = false, onArchived = {})
                }
            }
        }

        menu()
        compose.onNodeWithText("Physics").performClick()
        compose.onNodeWithText("Math").performClick()

        assertEquals(listOf<Any?>(2L, null), asked)
    }
}
