package com.seblful.klas.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * `animateItem()` for a row that **moves** within its list, and **no fade either way** — the app's one
 * way to animate a lazy list item.
 *
 * Fading is how one page of a list lingered on the next. An item leaving a `LazyColumn` keeps drawing at
 * its last place while it fades, so whenever the list's content was swapped rather than edited — a day
 * tap on the schedule, a view switch on a student's or group's card, a letter typed into a search — the
 * departing rows were printed over the arriving ones for a quarter of a second: two names on one line, or
 * a column of lesson cards laid across the month grid. On a 120 Hz phone that reads as a glitch, not as
 * motion.
 *
 * A swap is therefore instant, and only a genuine move slides — a lesson moved to another hour, a payment
 * re-sorted under a corrected date. Nothing is lost by it: a row a swipe removes has already left under
 * the thumb.
 *
 * Where one list shows several pages (the schedule's views and days), give each page its own item keys
 * too: a key shared across pages makes a swap look like every row moving.
 */
fun LazyItemScope.animateInPlace(modifier: Modifier = Modifier): Modifier =
    modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
