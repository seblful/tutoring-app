package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A setting's row draws the control [fitsInSegments] names — **and both of them work.**
 *
 * `SegmentsTest` pins the rule on the JVM; what it cannot ask is whether the branch it decides actually
 * produces a usable control, because a `@Composable` can only be exercised on a device. That matters here
 * more than for most rows: the field arm is reached only once a *fifth* option exists, so on today's build
 * it is dead code until the day somebody adds a currency — exactly the kind of arm that is written wrong and
 * discovered by a tutor.
 *
 * The labels are invented rather than read from resources: what is under test is the row, not any particular
 * setting, and inventing them is also what lets the count be the only thing that differs between the two
 * cases.
 */
class ChoiceRowTest {

    @get:Rule
    val rule = createComposeRule()

    private var picked: String? = null

    private fun show(count: Int) {
        val options = List(count) { index -> "v$index" to "Option $index" }
        rule.setContent {
            KlasTheme {
                ChoiceRow(
                    title = TITLE,
                    options = options,
                    selected = options.first().first,
                    onSelect = { value -> picked = value },
                )
            }
        }
    }

    /** At the bound, every option is on screen at once — that is what a segmented control is for. */
    @Test
    fun atTheBoundEveryOptionIsVisibleWithoutTapping() {
        show(MaxSegments)

        repeat(MaxSegments) { index -> rule.onNodeWithText("Option $index").assertIsDisplayed() }
        rule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun aSegmentReportsTheValueItStandsFor() {
        show(MaxSegments)

        rule.onNodeWithText("Option 2").performClick()

        assertEquals("v2", picked)
    }

    /**
     * One past the bound the row becomes a field: only the chosen option shows, the rest arrive on a tap.
     * The title moves *into* the field as its label, which is why it is still on screen — and the unchosen
     * options being absent is the whole difference being asserted.
     */
    @Test
    fun pastTheBoundTheOptionsAreBehindAField() {
        show(MaxSegments + 1)

        rule.onNodeWithText("Option 0").assertIsDisplayed()
        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onAllNodesWithText("Option 3").assertCountEquals(0)
        rule.onAllNodesWithText("Option 4").assertCountEquals(0)
    }

    @Test
    fun theFieldOpensAndReportsTheValuePickedFromIt() {
        show(MaxSegments + 1)

        rule.onNodeWithText("Option 0").performClick()
        rule.onNodeWithText("Option 4").performClick()

        assertEquals("the arm that only a fifth option reaches still selects", "v4", picked)
    }

    private companion object {
        const val TITLE = "A setting"
    }
}
