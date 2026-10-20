package com.seblful.klas.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MenuOptionsTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val students = (1L..300L).map { it to "Student $it" }

    private fun open(onSelect: (Long) -> Unit = {}) {
        compose.setContent {
            KlasTheme { DropdownField("Student", selectedText = "", options = students, onSelect = onSelect) }
        }
        compose.onNodeWithText("Student").performClick()
    }

    @Test
    fun `a long menu draws only its first matches and says how many more there are`() {
        open()

        compose.onNodeWithText("Student $MENU_SHOWN").assertExists()
        compose.onNodeWithText("Student ${MENU_SHOWN + 1}").assertDoesNotExist()
        compose.onNodeWithText("${300 - MENU_SHOWN} more — refine the search").assertExists()
    }

    @Test
    fun `searching a long menu reaches a name past the first matches`() {
        var selected: Long? = null
        open { selected = it }

        compose.onNodeWithText("Search").performTextInput("299")
        compose.onNodeWithText("Student 299").performClick()

        assertEquals(299L, selected)
    }
}
