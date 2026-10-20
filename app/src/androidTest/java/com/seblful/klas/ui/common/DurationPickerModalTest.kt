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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DurationPickerModalTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    private fun confirm() = rule.onNodeWithText(context.getString(R.string.action_done))

    @Test
    fun hoursAndMinutesAreConfirmedAsOneLength() {
        open(initialMinutes = 90)

        hours().performTextReplacement("2")
        minutes().performTextReplacement("5")
        confirm().performClick()

        assertEquals(125, confirmed)
    }

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
