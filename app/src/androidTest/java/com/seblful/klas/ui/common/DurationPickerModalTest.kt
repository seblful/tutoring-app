package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * «Другая» returns a **length**, and one that cannot be nonsense.
 *
 * This is the dialog that replaced a clock dial setting the lesson's end time, and the reason the
 * booking form no longer needs a rule for reading "ends at 22:00" on a lesson starting at 23:00. What
 * makes that hold is the shape of the input, so the shape is what is asserted: the fields refuse a day
 * and an hour respectively, so the longest answer is 23 ч 59 мин, and zero cannot be confirmed at all.
 *
 * The two fields are addressed by position rather than by their labels — a text field merges its label
 * into its own semantics, so matching «Часы» would match the field *and* be the thing under test.
 */
class DurationPickerModalTest {

    @get:Rule
    val rule = createComposeRule()

    /** The confirmed length, or null while nothing has been confirmed. */
    private var confirmed: Int? = null

    private fun open(initialMinutes: Int) {
        rule.setContent {
            KlasTheme {
                DurationPickerModal(
                    initialMinutes = initialMinutes,
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }
    }

    private fun hours() = rule.onAllNodes(hasSetTextAction())[0]
    private fun minutes() = rule.onAllNodes(hasSetTextAction())[1]
    private fun save() = rule.onNodeWithText("Сохранить")

    @Test
    fun hoursAndMinutesAreConfirmedAsOneLength() {
        open(initialMinutes = 90)

        hours().performTextReplacement("2")
        minutes().performTextReplacement("5")
        save().performClick()

        assertEquals(125, confirmed)
    }

    /** It opens on the length it was given, so confirming without touching anything changes nothing. */
    @Test
    fun theLengthItOpensOnIsTheOneItReturns() {
        open(initialMinutes = 50)

        save().performClick()

        assertEquals(50, confirmed)
    }

    @Test
    fun aDayOfHoursAndAnHourOfMinutesAreBothRefused() {
        open(initialMinutes = 60)

        hours().performTextReplacement("24")
        minutes().performTextReplacement("60")
        save().performClick()

        assertEquals("neither keystroke landed, so it is still the hour it opened on", 60, confirmed)
    }

    @Test
    fun aLessonOfNoLengthCannotBeConfirmed() {
        open(initialMinutes = 60)

        hours().performTextReplacement("0")
        minutes().performTextReplacement("0")

        save().assertIsNotEnabled()
        save().performClick()
        assertNull(confirmed)
    }
}
