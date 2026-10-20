package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test

class AvatarSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

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
}
