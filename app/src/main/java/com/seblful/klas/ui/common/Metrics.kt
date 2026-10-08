package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's non-negotiable measurements: how wide content may get, how small a target may be, and how far a
 * disabled thing fades.
 *
 * Beside `Spacing`, which owns the *rhythm*. These are the three figures that are not a rhythm but a floor
 * or a ceiling, and each is cited from more than one place — `Spacing`'s own KDoc names two of them as peers
 * of the scale, which they now are rather than being buried in a bucket of components.
 */

/**
 * The widest a single column of content should grow. On phones (always narrower than this) it's a
 * no-op and content stays full-width; on tablets, unfolded foldables and landscape it caps the line
 * length so text fields and list rows stay readable instead of stretching edge-to-edge.
 */
val MaxContentWidth = 600.dp
/**
 * Caps content at [MaxContentWidth] and centres it horizontally on wide screens, while leaving
 * phones full-width. Apply to a screen's top-level scrollable content (a scrolling `Column`, a
 * `LazyColumn`, or a settings list). Pairs with `fillMaxSize()`/`fillMaxWidth()` — put this after
 * the size modifier and before content padding, e.g. `Modifier.fillMaxSize().constrainedContent()`.
 */
fun Modifier.constrainedContent(max: Dp = MaxContentWidth): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)
/** The floor for anything tappable, per the Material accessibility guidance. */
val MinTouchTarget = 48.dp
/**
 * How much empty list is left under the last row of a screen with an add button, so the button never
 * covers one: the FAB's 56 dp and a gutter either side of it. A size, not a gap — it is measured against
 * the FAB rather than set to the app's rhythm, which is why it is here and not in `Spacing`.
 *
 * Shared because the lists that did not know it left 16 dp, and the last student, group or subject sat
 * under the button — its row unreachable, a tap on it opening «Новый» instead.
 */
val FabClearance = 88.dp
/**
 * The padding of a top-level list under an add button: the gutter on three sides and [FabClearance] under
 * the last row. One value because the four lists that carry one — students, groups, subjects and the
 * schedule — each spelled it out, and a list that drifted off it is how a last row ends up under the
 * button.
 */
val FabListPadding = PaddingValues(
    start = Spacing.gutter,
    top = Spacing.gutter,
    end = Spacing.gutter,
    bottom = FabClearance,
)
/** Material's standard opacity for disabled content. */
internal const val DisabledAlpha = 0.38f
