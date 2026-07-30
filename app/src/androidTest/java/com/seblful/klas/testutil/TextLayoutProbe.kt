package com.seblful.klas.testutil

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult

/**
 * The layout a `Text` was **actually drawn with**, read back out of its semantics node.
 *
 * The only way to assert anything about autosized text. `TextAutoSize` settles on a size during layout,
 * so the size that ends up on the screen is nowhere in the inputs: `layoutInput.style.fontSize` is still
 * the *asked-for* size — `titleMedium`'s 16sp for every tile in a `StatRow`, however small the tile
 * actually had to draw it. `GetTextLayoutResult` is the measured answer, and the measured answer is the
 * one the tutor reads.
 *
 * Shared because two suites now need it (the bottom bar's labels and the stat row's values) and it is not
 * a technique either of them should have to rediscover. What it gives back supports the three questions
 * worth asking of text that has to fit: `lineCount`, `isLineEllipsized(0)`, and `size.width` — the last
 * standing in for "at what size", since comparing one string's drawn width across two screen widths says
 * whether it shrank without needing the sp figure at all.
 *
 * @param text the string to find, matched exactly. `useUnmergedTree` because the interesting `Text` is
 *   usually inside something clickable, and a merged node reports its parent's semantics rather than the
 *   text node's own layout action.
 */
fun ComposeContentTestRule.textLayoutOf(text: String): TextLayoutResult =
    onNodeWithText(text, useUnmergedTree = true).textLayout()

/**
 * The same, for a node found some other way — by tag, when the text itself is not unique.
 *
 * Two `Text`s drawn with the *same string* and different styles is the shape of any assertion that
 * compares one rendering against another, and `onNodeWithText` cannot tell them apart by construction.
 */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(results)
    return results.first()
}
