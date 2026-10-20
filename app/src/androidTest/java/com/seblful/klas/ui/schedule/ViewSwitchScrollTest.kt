package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class ViewSwitchScrollTest {

    @get:Rule
    val rule = createComposeRule()

    private val leadingHeight = 280.dp
    private val rowHeight = 96.dp
    private val viewportHeight = 400.dp

    /**
     * @param resetOnChange whether the list is told the page changed — the line under test.
     */
    @Composable
    private fun Pager(showLeading: Boolean, resetOnChange: Boolean) {
        val state = rememberLazyListState()
        if (resetOnChange) {
            LaunchedEffect(showLeading) { state.scrollToItem(0) }
        }
        LazyColumn(state = state, modifier = Modifier.height(viewportHeight)) {
            if (showLeading) {
                item(key = "calendar") { Box(Modifier.fillMaxWidth().height(leadingHeight).testTag("calendar")) }
            }
            items(12, key = { it }) { index ->
                Box(Modifier.fillMaxWidth().height(rowHeight).testTag("row$index"))
            }
        }
    }

    @Test
    fun a_calendar_added_by_a_view_change_is_where_the_list_opens() {
        var showLeading by mutableStateOf(false)
        rule.setContent { Pager(showLeading = showLeading, resetOnChange = true) }

        rule.onNodeWithTag("row0").assertIsDisplayed()

        showLeading = true
        rule.waitForIdle()

        rule.onNodeWithTag("calendar").assertIsDisplayed()
    }

    @Test
    fun without_the_reset_the_calendar_lands_above_the_viewport() {
        var showLeading by mutableStateOf(false)
        rule.setContent { Pager(showLeading = showLeading, resetOnChange = false) }

        rule.onNodeWithTag("row0").assertIsDisplayed()

        showLeading = true
        rule.waitForIdle()

        rule.onNodeWithTag("calendar").assertIsNotDisplayed()
        rule.onNodeWithTag("row0").assertIsDisplayed()
    }
}
