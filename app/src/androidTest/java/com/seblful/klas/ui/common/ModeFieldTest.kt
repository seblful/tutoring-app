package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.res.stringResource
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
 *
 * `@HiltAndroidTest` for the reason `RevealedFocusTest` explains: it keeps an app component waking up
 * in the instrumentation process from killing the run.
 *
 * **The two words are read, not written.** They were the literals «Очно» and «Удалённо», which worked while
 * the app had one string folder: whatever locale the test device was in, there was only Russian to load. With
 * `values/` English and `values-ru/` Russian the words are now the *device's*, and hard-coding either
 * language would make this pass on one phone and fail on another — about nothing, since what is asserted is
 * the field's shape and not its vocabulary. Read through the same resources the composable draws from, so the
 * test and the screen cannot disagree.
 */
@HiltAndroidTest
class ModeFieldTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val inPerson: String = resources.getString(R.string.class_mode_in_person)
    private val remote: String = resources.getString(R.string.class_mode_remote)

    /** The mode last chosen, or null while nothing has been chosen. */
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

    /** The field itself, which is the only mode word on screen while the menu is shut. */
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
        // The field goes on showing the mode it was given — it displays what it is told to, and this test's
        // caller does not change it — so once the menu has gone that is the one left. Waited for rather than
        // asserted outright, because the menu leaves on an animation.
        rule.waitUntil { rule.onAllNodesWithText(inPerson).fetchSemanticsNodes().size == 1 }
        rule.onAllNodesWithText(remote).assertCountEquals(0)
    }
}
