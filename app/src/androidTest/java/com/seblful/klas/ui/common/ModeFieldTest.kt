package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The mode is picked from a **field**, the way a student or a subject is.
 *
 * What is asserted is the shape rather than the styling: the chosen mode is on screen before anything is
 * tapped — it used to be a label with two chips, which on the booking form was hidden behind a
 * [PickerField] until tapped, so one input had two appearances — the alternative is offered only once the
 * field is opened, and choosing it both reports the mode and closes the menu.
 */
class ModeFieldTest {

    @get:Rule
    val rule = createComposeRule()

    /** The mode last chosen, or null while nothing has been chosen. */
    private var chosen: ClassMode? = null

    private fun open(selected: ClassMode) {
        rule.setContent {
            KlasTheme {
                ModeField(label = "Место", selected = selected, onSelect = { mode -> chosen = mode })
            }
        }
    }

    /** The field itself, which is the only «Очно» or «Удалённо» on screen while the menu is shut. */
    private fun field(text: String) = rule.onNodeWithText(text)

    @Test
    fun theChosenModeIsReadableWithoutOpeningAnything() {
        open(selected = ClassMode.REMOTE)

        field("Удалённо").assertIsDisplayed()
        rule.onAllNodesWithText("Очно").assertCountEquals(0)
        assertNull("nothing was picked, so nothing was reported", chosen)
    }

    @Test
    fun theOtherModeIsOfferedOnlyOnceTheFieldIsOpened() {
        open(selected = ClassMode.IN_PERSON)
        rule.onAllNodesWithText("Удалённо").assertCountEquals(0)

        field("Очно").performClick()

        rule.onNodeWithText("Удалённо").assertIsDisplayed()
    }

    @Test
    fun pickingFromTheMenuReportsTheModeAndClosesIt() {
        open(selected = ClassMode.IN_PERSON)

        field("Очно").performClick()
        rule.onNodeWithText("Удалённо").performClick()

        assertEquals(ClassMode.REMOTE, chosen)
        // The field goes on showing «Очно» — it displays what it is told to, and this test's caller does
        // not change it — so once the menu has gone that is the one «Очно» left. Waited for rather than
        // asserted outright, because the menu leaves on an animation.
        rule.waitUntil { rule.onAllNodesWithText("Очно").fetchSemanticsNodes().size == 1 }
        rule.onAllNodesWithText("Удалённо").assertCountEquals(0)
    }
}
