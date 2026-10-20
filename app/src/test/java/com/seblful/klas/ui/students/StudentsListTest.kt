package com.seblful.klas.ui.students

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS")
class StudentsListTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `a settled student says nothing about money, and their card is as tall as one with a debt`() {
        compose.setContent {
            KlasTheme {
                StudentsList(
                    state = Loadable.Ready(
                        listOf(
                            StudentRow(Student(id = 1, name = "Ava"), balance = Money.ofMajor(90)),
                            StudentRow(Student(id = 2, name = "Chloe"), balance = Money.ZERO),
                        ),
                    ),
                    searching = false,
                    onStudentClick = {},
                    onAddStudent = {},
                )
            }
        }

        compose.onNodeWithText("Owes", substring = true).assertExists()
        compose.onNodeWithText("Nothing owed").assertDoesNotExist()
        assertEquals(cardHeight("Ava"), cardHeight("Chloe"))
    }

    /** The height of the clickable card holding [name] — the node that merges the row's text. */
    private fun cardHeight(name: String): Int =
        compose.onNodeWithText(name, substring = true).fetchSemanticsNode().size.height
}
