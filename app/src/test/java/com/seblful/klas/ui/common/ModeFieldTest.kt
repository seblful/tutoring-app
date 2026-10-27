package com.seblful.klas.ui.common

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ModeFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val inPerson: String = resources.getString(R.string.class_mode_in_person)
    private val remote: String = resources.getString(R.string.class_mode_remote)

    private var chosen: ClassMode? = null

    private fun open(selected: ClassMode) {
        rule.setContent {
            KlasTheme {
                ModeField(
                    label = stringResource(R.string.class_location_label),
                    selected = selected,
                    onSelect = { mode -> chosen = mode },
                )
            }
        }
    }

    private fun field(text: String) = rule.onNodeWithText(text)

    @Test
    fun theChosenModeIsReadableWithoutOpeningAnything() {
        open(selected = ClassMode.REMOTE)

        field(remote).assertIsDisplayed()
        rule.onAllNodesWithText(inPerson).assertCountEquals(0)
        assertNull("nothing was picked, so nothing was reported", chosen)
    }

    @Test
    fun theOtherModeIsOfferedOnlyOnceTheFieldIsOpened() {
        open(selected = ClassMode.IN_PERSON)
        rule.onAllNodesWithText(remote).assertCountEquals(0)

        field(inPerson).performClick()

        rule.onNodeWithText(remote).assertIsDisplayed()
    }

    @Test
    fun pickingFromTheMenuReportsTheModeAndClosesIt() {
        open(selected = ClassMode.IN_PERSON)

        field(inPerson).performClick()
        rule.onNodeWithText(remote).performClick()

        assertEquals(ClassMode.REMOTE, chosen)
        rule.waitUntil { rule.onAllNodesWithText(inPerson).fetchSemanticsNodes().size == 1 }
        rule.onAllNodesWithText(remote).assertCountEquals(0)
    }
}
