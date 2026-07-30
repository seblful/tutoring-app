package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
@HiltAndroidTest
class DurationPickerModalTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    /**
     * The button that closes the modal — «Готово», and **not** «Сохранить».
     *
     * Named `confirm` rather than `save` because that distinction is the point: a picker returns a value
     * to the form it was opened from and writes nothing, so the booking form used to ask the tutor to save
     * twice in a row, the first of which plausibly read as having committed the booking. All three modals
     * in `Pickers.kt` were moved onto `action_done` for it.
     *
     * Read from resources rather than typed as a literal, which is the whole reason this comment exists:
     * the label was hard-coded here as «Сохранить», so when the modals moved, this suite went red and
     * stayed red — instrumented tests need a device, CI only compiles them, and nothing ran the full suite
     * again until long afterwards. A test that spells a user-facing string by hand is a test that has to be
     * remembered; this one cannot drift from the button it presses.
     */
    private fun confirm() = rule.onNodeWithText(context.getString(R.string.action_done))

    @Test
    fun hoursAndMinutesAreConfirmedAsOneLength() {
        open(initialMinutes = 90)

        hours().performTextReplacement("2")
        minutes().performTextReplacement("5")
        confirm().performClick()

        assertEquals(125, confirmed)
    }

    /** It opens on the length it was given, so confirming without touching anything changes nothing. */
    @Test
    fun theLengthItOpensOnIsTheOneItReturns() {
        open(initialMinutes = 50)

        confirm().performClick()

        assertEquals(50, confirmed)
    }

    @Test
    fun aDayOfHoursAndAnHourOfMinutesAreBothRefused() {
        open(initialMinutes = 60)

        hours().performTextReplacement("24")
        minutes().performTextReplacement("60")
        confirm().performClick()

        assertEquals("neither keystroke landed, so it is still the hour it opened on", 60, confirmed)
    }

    @Test
    fun aLessonOfNoLengthCannotBeConfirmed() {
        open(initialMinutes = 60)

        hours().performTextReplacement("0")
        minutes().performTextReplacement("0")

        confirm().assertIsNotEnabled()
        confirm().performClick()
        assertNull(confirmed)
    }
}
