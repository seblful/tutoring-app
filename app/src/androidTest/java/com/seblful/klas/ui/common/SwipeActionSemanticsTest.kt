package com.seblful.klas.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeActionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private var confirmed = 0

    private fun row() {
        rule.setContent {
            KlasTheme {
                SwipeToConfirmBox(
                    onConfirm = { confirmed++ },
                    reveal = { Text(REVEAL) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(
                        "занятие",
                        Modifier
                            .testTag(ROW)
                            .clickable { }
                            .swipeAction(ACTION) { confirmed++ },
                    )
                }
            }
        }
    }

    @Test
    fun theSwipeIsOfferedAsTheRowsOwnAction() {
        row()

        val actions = rule.onNodeWithTag(ROW).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf(ACTION), actions.map { it.label })

        rule.runOnIdle { actions.single().action() }
        rule.runOnIdle { assertEquals("the action does what the swipe does", 1, confirmed) }
    }

    @Test
    fun thePanelBehindTheRowIsNotRead() {
        row()

        rule.onNodeWithText(REVEAL).assertDoesNotExist()
        rule.onNodeWithText("занятие").assertExists()
    }

    private companion object {
        const val ROW = "row"
        const val ACTION = "Отметить проведённым"
        const val REVEAL = "Проведено"
    }
}
