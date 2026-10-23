package com.seblful.klas.ui.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KlasRowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a toggle row is one switch, toggled by a tap anywhere on it`() {
        var on = false
        compose.setContent {
            KlasTheme { KlasRow("Weekly digest", supporting = "Mondays", end = RowEnd.Toggle(on) { on = it }) }
        }

        compose.onNodeWithText("Weekly digest", useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()

        assertTrue(on)
    }

    @Test
    fun `a row that opens something says what a tap does`() {
        compose.setContent {
            KlasTheme { KlasRow("Anna", end = RowEnd.Opens, onClick = {}, onClickLabel = "Open the student") }
        }

        val label = compose.onNodeWithText("Anna").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label
        assertEquals("Open the student", label)
    }

    @Test
    fun `an amount is read with its words`() {
        compose.setContent {
            KlasTheme { KlasRow("Anna", end = RowEnd.Money(AmountText("Owes 200 $", AmountMeaning.Debt))) }
        }

        compose.onNodeWithText("Owes 200 $").assertExists()
    }

    @Test
    fun `an icon action is named for TalkBack and runs on its own`() {
        var removed = 0
        var opened = 0
        compose.setContent {
            KlasTheme {
                KlasRow(
                    "Algebra",
                    onClick = { opened++ },
                    end = RowEnd.Action(KlasIcons.Outlined.Delete, "Remove Algebra") { removed++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Remove Algebra").performClick()

        assertEquals(1, removed)
        assertEquals(0, opened)
    }

    @Test
    fun `a disabled row refuses taps`() {
        var on = true
        compose.setContent {
            KlasTheme { KlasRow("Reminders", enabled = false, end = RowEnd.Toggle(on) { on = it }) }
        }

        compose.onNodeWithText("Reminders").assertIsOn().assertIsNotEnabled().performClick()

        assertTrue(on)
    }
}
