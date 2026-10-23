package com.seblful.klas.ui.common

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class TabReselectTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val reselect = TabReselect()
    private lateinit var scope: CoroutineScope

    private fun tapTabAgain() {
        compose.runOnIdle { scope.launch { reselect.reselected() } }
        compose.waitForIdle()
    }

    @Test
    fun `a second tap on the tab brings a scrolled list back to its first row`() {
        compose.setContent {
            scope = rememberCoroutineScope()
            KlasTheme {
                CompositionLocalProvider(LocalTabReselect provides reselect) {
                    KlasScreen("People") { _, _ ->
                        val list = rememberLazyListState()
                        ScrollsToTopOnReselect(list)
                        LazyColumn(state = list) { items(ROWS) { Text("Row $it") } }
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(ROWS - 1)
        compose.onNodeWithText("Row 0").assertDoesNotExist()

        tapTabAgain()

        compose.onNodeWithText("Row 0").assertIsDisplayed()
    }

    @Test
    fun `a page no longer on show is not scrolled`() {
        var shown by mutableStateOf(true)
        var scrolls = 0
        compose.setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                if (shown) ScrollsToTopOnReselect(atTop = { false }) { scrolls++ }
            }
        }

        tapTabAgain()
        compose.runOnIdle { shown = false }
        tapTabAgain()

        assertEquals(1, scrolls)
    }

    @Test
    fun `on a later view a second tap scrolls to the top first and the next opens the first view`() {
        var view by mutableStateOf(Week)
        showViews(view = { view }, onSelect = { view = it })
        compose.onNode(hasScrollAction()).performScrollToIndex(ROWS - 1)

        tapTabAgain()

        compose.onNodeWithText("Row 0").assertIsDisplayed()
        assertEquals(Week, view)

        tapTabAgain()

        assertEquals(Day, view)
    }

    @Test
    fun `a tap on a later view already at the top opens the first view`() {
        var view by mutableStateOf(Week)
        showViews(view = { view }, onSelect = { view = it })

        tapTabAgain()

        assertEquals(Day, view)
    }

    private fun showViews(view: () -> String, onSelect: (String) -> Unit) {
        compose.setContent {
            scope = rememberCoroutineScope()
            KlasTheme {
                CompositionLocalProvider(LocalTabReselect provides reselect) {
                    KlasScreen("Schedule") { _, _ ->
                        SegmentedPage(listOf(Day, Week), view(), onSelect, label = { it }) {
                            val list = rememberLazyListState()
                            ScrollsToTopOnReselect(list)
                            LazyColumn(state = list) { items(ROWS) { Text("Row $it") } }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val ROWS = 60
        const val Day = "Day"
        const val Week = "Week"
    }
}
