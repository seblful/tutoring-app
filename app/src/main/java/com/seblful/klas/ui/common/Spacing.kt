package com.seblful.klas.ui.common

import androidx.compose.ui.unit.dp

/**
 * The distances the app lays out with — five values, on Material's 4 dp grid.
 *
 * `MaxContentWidth` and [MinTouchTarget] were named constants while every padding and gap was a bare
 * literal, and the result was fourteen distinct values across `ui/` — including 5, 10, 11 and 14 dp,
 * which are off the grid entirely. None of them was wrong on its own. Together they meant that the
 * same visual pattern, "cards in a scrolling list", was drawn four different ways: the students and
 * groups lists spaced their cards 10 dp apart, the report 12, and the schedule 5 dp of padding on
 * each item plus 11 at the top of the list to make the first card land at 16.
 *
 * The names answer the questions a layout actually asks:
 *
 * - [gutter] — how far content sits from the edge of the screen.
 * - [itemGap] — between two items in the same list.
 * - [sectionGap] — between two groups of items, where the gap is what says they are separate.
 * - [inline] — between a glyph and its label, a badge and the text it qualifies: parts of one thing.
 * - [leading] — between a row's leading content (an avatar, a wide icon) and the text beside it.
 * - [tight] — a stacked label and its value, which are one statement on two lines.
 *
 * [leading] and [gutter] hold the same number and are still two entries, because they answer
 * different questions and Material only happens to answer both with 16: one is this app's rhythm, the
 * other is the list-item spec. The avatar-to-name distance was 16 dp on the students list, 12 on the
 * group card and in the roster picker, and 10 on the student card — the four-way drift that a single
 * shared literal would have made look intentional.
 *
 * Sizes are deliberately **not** here. [MinTouchTarget], `KindIconInk`, a calendar cell's height and
 * a skeleton row's are measurements of particular things, answerable only against the thing; a gap is
 * a rhythm and there is one of those per app.
 *
 * ### The list owns the gutter
 *
 * A scrolling list applies [gutter] through its own `contentPadding`, and its items apply none. The
 * schedule did it the other way round — each row padded itself — which costs the two things
 * `contentPadding` exists for: the first and last item get the same inset as the sides without the
 * scrollbar being pushed in, and a fling still shows content travelling under the app bar rather
 * than a padded box sliding within one. It also means an item can be moved between lists without
 * carrying a gutter that belongs to where it used to be.
 */
object Spacing {

    /** A stacked label and its value — one statement, two lines. */
    val tight = 4.dp

    /** Between parts of a single thing — a small icon and its label, a name and its badge. */
    val inline = 8.dp

    /** Between two items in one list. */
    val itemGap = 12.dp

    /** Content's distance from the screen edge. Applied by the list, never by the item. */
    val gutter = 16.dp

    /** A row's leading avatar or icon to the text beside it. Material's list-item spec. */
    val leading = 16.dp

    /** Between two sections, where the gap itself is what separates them. */
    val sectionGap = 24.dp
}
