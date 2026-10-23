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
                        ScrollsToTopOnReselect { list.animateScrollToItem(0) }
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
                if (shown) ScrollsToTopOnReselect { scrolls++ }
            }
        }

        tapTabAgain()
        compose.runOnIdle { shown = false }
        tapTabAgain()

        assertEquals(1, scrolls)
    }

    private companion object {
        const val ROWS = 60
    }
}
