package com.seblful.klas.ui.common

import androidx.compose.ui.unit.dp

/** Layout gaps on the 4 dp grid. Lists own [gutter]; items own the spacing between their contents. */
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
