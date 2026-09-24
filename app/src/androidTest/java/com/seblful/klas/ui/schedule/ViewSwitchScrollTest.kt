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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * **A change of view is a new page, and a page starts at the top** — `ScheduleScreen`'s
 * `LaunchedEffect(viewMode)` that scrolls the list to its first item, and the reason it has to exist.
 * (Only on an actual change of view: the screen keeps which view it last reset for, so a return to the
 * schedule keeps where the tutor had scrolled.)
 *
 * The schedule's three views share one `LazyColumn`, and the month grid and the week strip are `item`s
 * *in* it: tapping «Месяц» prepends one, tapping «День» takes it away. A `LazyColumn` deliberately keeps
 * the item that was on top on top — it re-finds it by key when the content changes, which is what stops a
 * list jumping when a row is inserted above — so the grid was inserted **above the viewport**, and «Месяц»
 * opened on the lesson list with nothing but the hairline under the calendar to say a month had been drawn
 * off-screen. On a device it read as the calendar being gone.
 *
 * Asserted on a `LazyColumn` of the same shape rather than on the screen: what is under test is the
 * interaction between prepending a keyed item and the list's scroll anchor, and a harness says which of
 * those two facts each assertion is about. `SwipeToConfirmBoxTest` makes the same trade for the same
 * reason — some rules are only true on a device, and the smallest thing that can be wrong is what to test.
 */
@HiltAndroidTest
class ViewSwitchScrollTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    /** Roughly the month grid: taller than the viewport's share of it, as the real one is. */
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
            // Keyed like the real one (`item(key = "calendar")`), because the key is what the list's
            // anchor is expressed in — an unkeyed item would be a different test.
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

        // The day view: rows from the top, no calendar.
        rule.onNodeWithTag("row0").assertIsDisplayed()

        showLeading = true
        rule.waitForIdle()

        rule.onNodeWithTag("calendar").assertIsDisplayed()
    }

    /**
     * And the same switch **without** the reset, which is what the app did: the list holds its anchor row
     * in place and the calendar lands above the viewport. This pins the reason the effect exists rather
     * than any behaviour of the app's own — if Compose ever stopped anchoring, this is the test that would
     * say the line can go.
     */
    @Test
    fun without_the_reset_the_calendar_lands_above_the_viewport() {
        var showLeading by mutableStateOf(false)
        rule.setContent { Pager(showLeading = showLeading, resetOnChange = false) }

        rule.onNodeWithTag("row0").assertIsDisplayed()

        showLeading = true
        rule.waitForIdle()

        // Composed — the list keeps it as the item before its anchor — but off the top of the viewport.
        rule.onNodeWithTag("calendar").assertIsNotDisplayed()
        rule.onNodeWithTag("row0").assertIsDisplayed()
    }
}
