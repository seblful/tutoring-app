package com.seblful.klas.ui.schedule.booking

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoldTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun show() {
        compose.setContent {
            KlasTheme {
                var opened by remember { mutableStateOf(false) }
                Fold(label = { Text("Repeat") }, summary = "Once", opened = opened, onToggle = { opened = it }) {
                    Text("Every week")
                }
            }
        }
    }

    @Test
    fun `folded it says what it is set to and hides its fields`() {
        compose.onNodeWithText("Once").assertIsDisplayed()
        compose.onNodeWithText("Every week").assertDoesNotExist()
    }

    @Test
    fun `a tap opens it to its fields, and another folds it again`() {
        compose.onNodeWithTag(FOLD_TAG).performClick()
        compose.onNodeWithText("Every week").assertIsDisplayed()
        compose.onNodeWithText("Once").assertDoesNotExist()

        compose.onNodeWithTag(FOLD_TAG).performClick()
        compose.onNodeWithText("Every week").assertDoesNotExist()
        compose.onNodeWithText("Once").assertIsDisplayed()
    }

    @Test
    fun `a screen reader expands and collapses it`() {
        compose.onNodeWithTag(FOLD_TAG).performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithText("Every week").assertIsDisplayed()

        compose.onNodeWithTag(FOLD_TAG).performSemanticsAction(SemanticsActions.Collapse)
        compose.onNodeWithText("Every week").assertDoesNotExist()
    }
}
