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
