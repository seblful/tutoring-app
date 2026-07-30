package com.seblful.klas.ui.common

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a screen draws while its state is [Loadable.Loading].
 *
 * **Shaped like the content, not a spinner in the middle.** A centred indicator gives the eye nothing to
 * settle on and then reflows the whole screen when the data lands; blocks the size of the rows that are
 * coming mean the first real frame changes colour rather than layout. That is the difference between a
 * list that loads and a list that jumps.
 *
 * Silent to a screen reader. A skeleton is scenery for a wait, and announcing five unlabelled blocks
 * would be worse than announcing nothing — the content that replaces them is what has something to say.
 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    rowHeight: Dp = SkeletonRowHeight,
    contentPadding: PaddingValues = PaddingValues(Spacing.gutter),
) {
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        repeat(rows) { SkeletonBlock(rowHeight) }
    }
}

/**
 * One placeholder shape, at the tonal ladder's item rung so it reads as a card that has not filled in
 * yet rather than as a hole in the page.
 *
 * @param height the height of the row being stood in for. The caller knows it; this does not.
 */
@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = placeholderAlpha())),
    ) {}
}

/**
 * The faint pulse that says the wait is live.
 *
 * **Held still where the phone asks for it.** Animations can be switched off system-wide (developer
 * options, or a battery saver), and an app that keeps pulsing through that setting is ignoring the same
 * kind of instruction the app already honours for haptics — see `Haptics.kt`. Read once per composition
 * rather than observed: this is a setting a tutor changes in system settings, which restarts the
 * activity anyway.
 */
@Composable
private fun placeholderAlpha(): Float {
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
    if (!animate) return (MinPlaceholderAlpha + MaxPlaceholderAlpha) / 2f

    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = MinPlaceholderAlpha,
        targetValue = MaxPlaceholderAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

/**
 * A detail card before its subject has been read: the stat strip, the tab row, and rows under it.
 *
 * Shared by the student's card and the group's, which have the same skeleton because they have the same
 * skeleton — numbers on top, a tab row, one list. Their *contents* differ and their loading shape does
 * not, so this is one composable rather than the two near-identical private ones the cards would
 * otherwise have grown (they already did that with `TabEmptyState`).
 *
 * @param onBack the back arrow stays live throughout. A screen that cannot be left until it has loaded
 *   is a screen that traps the tutor if the load is slow.
 */
@Composable
fun LoadingDetailScreen(onBack: () -> Unit) {
    KlasScreen(
        // No title: the title is the name of the thing being loaded, and inventing a placeholder for it
        // would be the one piece of this screen that lies rather than waits.
        title = null,
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.itemGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            SkeletonBlock(StatRowHeight)
            SkeletonBlock(TabRowHeight, shape = MaterialTheme.shapes.extraSmall)
            ListSkeleton(rows = 4, rowHeight = DetailRowHeight, contentPadding = PaddingValues(0.dp))
        }
    }
}

/** Roughly a `ContentCard` holding a 40dp avatar and two lines of text. */
private val SkeletonRowHeight = 72.dp

/** Roughly a `StatRow`: three tiles of a value, a label and a hint, at 12dp vertical padding. */
private val StatRowHeight = 76.dp

/** Roughly a `SecondaryTabRow`. */
private val TabRowHeight = 48.dp

/** Roughly a `ListItem` with a leading avatar and a supporting line. */
private val DetailRowHeight = 64.dp

private const val MinPlaceholderAlpha = 0.06f
private const val MaxPlaceholderAlpha = 0.14f
private const val PulseMillis = 900
