package com.seblful.klas.ui.designsystem.component

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class FilterBarTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `three options are three chips`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            KlasTheme {
                FilterBar(listOf("All", "Maths", "Physics").map { FilterOption(it, it == "All", { picked += it }) })
            }
        }

        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Physics").performClick()
        compose.onNodeWithText("Filter").assertDoesNotExist()
        assertEquals(listOf("Physics"), picked)
    }

    @Test
    fun `past three, the rest are behind a filter chip that names the one chosen`() {
        val picked = mutableListOf<String>()
        val labels = listOf("All", "Maths", "Physics", "Chemistry")
        compose.setContent {
            KlasTheme {
                FilterBar(labels.map { FilterOption(it, it == "Chemistry", { picked += it }) })
            }
        }

        compose.onNodeWithText("Physics").assertDoesNotExist()
        compose.onNodeWithText("Chemistry").assertIsSelected().performClick()
        compose.onNodeWithText("Physics").performClick()
        assertEquals(listOf("Physics"), picked)
    }
}
