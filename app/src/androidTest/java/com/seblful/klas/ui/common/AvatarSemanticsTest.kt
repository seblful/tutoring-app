package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test

class AvatarSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aMonogramIsNotReadOutBesideTheNameItStandsFor() {
        composeRule.setContent {
            KlasTheme {
                Row {
                    Avatar(name = "Ирина", studentId = 1)
                    Text("Ирина Петрова")
                }
            }
        }

        composeRule.onNodeWithText("Ирина Петрова").assertIsDisplayed()
        composeRule.onNodeWithText("И").assertDoesNotExist()
    }

    @Test
    fun aStackIsAnnouncedAsItsWholeRosterRatherThanItsVisibleFaces() {
        val names = listOf("Аня", "Борис", "Вера", "Галина", "Дмитрий", "Егор")
        composeRule.setContent {
            KlasTheme {
                AvatarStack(names = names, ids = names.indices.map { it.toLong() })
            }
        }

        val expected = context.getString(R.string.roster_members, names.joinToString(", "))
        composeRule.onNodeWithContentDescription(expected).assertExists()
        composeRule.onNodeWithText("+2").assertDoesNotExist()
    }
}
