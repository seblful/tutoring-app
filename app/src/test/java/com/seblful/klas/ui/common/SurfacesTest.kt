package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SurfacesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `section titles are announced as headings`() {
        compose.setContent { KlasTheme { SectionTitle("Billing") } }

        compose.onNodeWithText("Billing")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun `a selected card announces its selection and activates once`() {
        var clicks = 0
        compose.setContent {
            KlasTheme { ContentCard(onClick = { clicks++ }, selected = true) { Text("Student") } }
        }

        compose.onNodeWithText("Student").assertIsSelected().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a badge keeps its label and a sheet handle offers no action`() {
        compose.setContent {
            KlasTheme {
                Column {
                    StatusBadge("Completed", Color.Green)
                    SheetHandle(Modifier.testTag("handle"))
                }
            }
        }

        compose.onNodeWithText("Completed").assertIsDisplayed()
        compose.onNodeWithTag("handle").assertHasNoClickAction()
    }

    @Test
    fun `a sheet handle given a way to close names itself and dismisses`() {
        var dismissed = 0
        compose.setContent { KlasTheme { SheetHandle(Modifier.testTag("handle"), onDismiss = { dismissed++ }) } }

        compose.onNodeWithTag("handle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Drag handle")))
            .performSemanticsAction(SemanticsActions.Dismiss)

        assertEquals(1, dismissed)
    }
}
